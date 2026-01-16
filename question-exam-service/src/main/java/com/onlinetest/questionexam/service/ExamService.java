package com.onlinetest.questionexam.service;

import com.onlinetest.questionexam.dto.ExamCreateRequestDTO;
import com.onlinetest.questionexam.dto.ExamQuestionViewDTO;
import com.onlinetest.questionexam.dto.ExamResponseDTO;
import com.onlinetest.questionexam.dto.ExamTopicSummaryDTO;
import com.onlinetest.questionexam.dto.PaginatedResponseDTO;
import com.onlinetest.questionexam.entity.Exam;
import com.onlinetest.questionexam.entity.ExamQuestion;
import com.onlinetest.questionexam.entity.Question;
import com.onlinetest.questionexam.entity.Topic;
import com.onlinetest.questionexam.repository.ExamQuestionRepository;
import com.onlinetest.questionexam.repository.ExamRepository;
import com.onlinetest.questionexam.repository.QuestionRepository;
import com.onlinetest.questionexam.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ExamService {

    private final ExamRepository examRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final TopicRepository topicRepository;
    private final QuestionRepository questionRepository;

    // =========================
    // Create Exam
    // =========================
    public ExamResponseDTO createExam(ExamCreateRequestDTO req, Long companyId, Long userId, String role) {

        int totalQuestions = 0;
        List<String> validationErrors = new ArrayList<>();

        for (ExamCreateRequestDTO.ExamTopicRequestDTO t : req.getTopics()) {

            Topic topic = topicRepository.findByIdAndCompanyId(t.getTopicId(), companyId)
                    .orElse(null);

            if (topic == null) {
                validationErrors.add("Topic not found");
                continue;
            }

            List<Long> activeIds =
                    questionRepository.findActiveIdsByCompanyAndTopic(companyId, topic.getId());

            if (activeIds.size() < t.getQuestionsCount()) {
                validationErrors.add(
                        "Topic \"" + topic.getName() + "\" has only "
                                + activeIds.size() + " active questions, but "
                                + t.getQuestionsCount() + " were requested"
                );
            }

            totalQuestions += t.getQuestionsCount();
        }

        if (!validationErrors.isEmpty()) {
            throw new RuntimeException(
                    "Some selected topics do not have enough active questions. Please review topic selection."
            );
        }

        SecureRandom rnd = new SecureRandom();
        List<Long> chosen = new ArrayList<>(totalQuestions);

        for (ExamCreateRequestDTO.ExamTopicRequestDTO t : req.getTopics()) {
            List<Long> ids =
                    questionRepository.findActiveIdsByCompanyAndTopic(companyId, t.getTopicId());
            Collections.shuffle(ids, rnd);
            chosen.addAll(ids.subList(0, t.getQuestionsCount()));
        }

        Collections.shuffle(chosen, rnd);

        Exam exam = new Exam();
        exam.setCompanyId(companyId);
        exam.setTitle(req.getTitle());
        exam.setDescription(req.getDescription());
        exam.setTotalQuestions(totalQuestions);

        if (req.getPassingPercentage() != null) {
            exam.setPassingPercentage(req.getPassingPercentage());
        }

        exam.setPerQuestionSeconds(req.getPerQuestionSeconds());
        exam.setReviewMinutes(req.getReviewMinutes());
        exam.setCreatedBy(userId);
        exam.setCreatedByRole(role);

        Exam saved = examRepository.save(exam);

        int pos = 1;
        for (Long qid : chosen) {
            ExamQuestion eq = new ExamQuestion();
            eq.setExamId(saved.getId());
            eq.setQuestionId(qid);
            eq.setPosition(pos++);
            examQuestionRepository.save(eq);
        }

        List<ExamTopicSummaryDTO> topics = deriveSelectedTopics(saved.getId());
        return mapToResponseDTO(saved, topics.size(), topics);
    }

    // =========================
    // Get Exam Details
    // =========================
    @Transactional(readOnly = true)
    public ExamResponseDTO getExamById(Long examId, Long companyId) {

        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found: " + examId));

        if (!exam.getCompanyId().equals(companyId)) {
            throw new RuntimeException("Exam not found: " + examId);
        }

        List<ExamTopicSummaryDTO> topics = deriveSelectedTopics(examId);
        return mapToResponseDTO(exam, topics.size(), topics);
    }

    // =========================
    // Get Exam Questions (For Delivery)
    // =========================
    @Transactional(readOnly = true)
    public List<ExamQuestionViewDTO> getExamQuestionsForDelivery(Long examId, Long companyId) {

        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found: " + examId));

        if (!exam.getCompanyId().equals(companyId)) {
            throw new RuntimeException("Exam not found: " + examId);
        }

        List<ExamQuestion> examQuestions =
                examQuestionRepository.findByExamIdOrderByPositionAsc(examId);

        List<Question> questions =
                questionRepository.findAllById(
                        examQuestions.stream()
                                .map(ExamQuestion::getQuestionId)
                                .toList()
                );

        return examQuestions.stream()
                .map(eq -> {
                    Question q = questions.stream()
                            .filter(qq -> qq.getId().equals(eq.getQuestionId()))
                            .findFirst()
                            .orElse(null);

                    if (q == null) return null;

                    return ExamQuestionViewDTO.builder()
                            .id(q.getId())
                            .topicId(q.getTopicId())
                            .topicName(q.getTopic() != null ? q.getTopic().getName() : null)
                            .questionText(q.getQuestionText())
                            .optionA(q.getOptionA())
                            .optionB(q.getOptionB())
                            .optionC(q.getOptionC())
                            .optionD(q.getOptionD())
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }

    // =========================
    // List Exams
    // =========================
    @Transactional(readOnly = true)
    public List<ExamResponseDTO> listCompanyExams(Long companyId) {
        return examRepository.findAll().stream()
                .filter(e -> e.getCompanyId().equals(companyId))
                .map(e -> {
                    List<ExamTopicSummaryDTO> topics = deriveSelectedTopics(e.getId());
                    return mapToResponseDTO(e, topics.size(), topics);
                })
                .toList();
    }

    // =========================
    // Search + Pagination
    // =========================
    @Transactional(readOnly = true)
    public PaginatedResponseDTO<ExamResponseDTO> searchExams(Long companyId, String search, int page) {

        int pageSize = 5;
        Pageable pageable = PageRequest.of(page, pageSize);

        Page<Exam> examPage;

        if (search != null && !search.trim().isEmpty()) {
            examPage = examRepository.findByCompanyIdAndTitleContainingIgnoreCase(
                    companyId,
                    search.trim(),
                    pageable
            );
        } else {
            examPage = examRepository.findByCompanyId(companyId, pageable);
        }

        List<ExamResponseDTO> content = examPage.getContent().stream()
                .map(e -> {
                    List<ExamTopicSummaryDTO> topics = deriveSelectedTopics(e.getId());
                    return mapToResponseDTO(e, topics.size(), topics);
                })
                .toList();

        return PaginatedResponseDTO.<ExamResponseDTO>builder()
                .content(content)
                .currentPage(examPage.getNumber())
                .pageSize(pageSize)
                .totalPages(examPage.getTotalPages())
                .totalElements(examPage.getTotalElements())
                .hasNext(examPage.hasNext())
                .hasPrevious(examPage.hasPrevious())
                .build();
    }

    // =========================
    // Update Exam
    // =========================
    public ExamResponseDTO updateExam(Long examId, ExamCreateRequestDTO req,
                                      Long companyId, Long userId, String role) {

        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found: " + examId));

        if (!exam.getCompanyId().equals(companyId)) {
            throw new RuntimeException("Exam not found: " + examId);
        }

        exam.setTitle(req.getTitle());
        exam.setDescription(req.getDescription());

        if (req.getPassingPercentage() != null) {
            exam.setPassingPercentage(req.getPassingPercentage());
        }

        exam.setPerQuestionSeconds(req.getPerQuestionSeconds());
        exam.setReviewMinutes(req.getReviewMinutes());
        exam.setUpdatedBy(userId);
        exam.setUpdatedByRole(role);

        examRepository.save(exam);

        List<ExamTopicSummaryDTO> topics = deriveSelectedTopics(examId);
        return mapToResponseDTO(exam, topics.size(), topics);
    }

    // =========================
    // Delete Exam
    // =========================
    public void deleteExam(Long examId, Long companyId) {

        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found: " + examId));

        if (!exam.getCompanyId().equals(companyId)) {
            throw new RuntimeException("Exam not found: " + examId);
        }

        examQuestionRepository.deleteByExamId(examId);
        examRepository.delete(exam);
    }

    // =========================
    // Helpers
    // =========================
    private List<ExamTopicSummaryDTO> deriveSelectedTopics(Long examId) {

        List<ExamQuestion> examQuestions =
                examQuestionRepository.findByExamIdOrderByPositionAsc(examId);

        List<Question> questions = questionRepository.findAllById(
                examQuestions.stream()
                        .map(ExamQuestion::getQuestionId)
                        .toList()
        );

        return questions.stream()
                .collect(Collectors.groupingBy(Question::getTopicId))
                .entrySet()
                .stream()
                .map(entry -> {
                    Question q = entry.getValue().get(0);
                    return new ExamTopicSummaryDTO(
                            entry.getKey(),
                            q.getTopic() != null ? q.getTopic().getName() : "Unknown",
                            entry.getValue().size()
                    );
                })
                .toList();
    }

    private ExamResponseDTO mapToResponseDTO(
            Exam exam,
            Integer topicCount,
            List<ExamTopicSummaryDTO> selectedTopics
    ) {
        return ExamResponseDTO.builder()
                .id(exam.getId())
                .companyId(exam.getCompanyId())
                .title(exam.getTitle())
                .description(exam.getDescription())
                .totalQuestions(exam.getTotalQuestions())
                .perQuestionSeconds(exam.getPerQuestionSeconds())
                .reviewMinutes(exam.getReviewMinutes())
                .passingPercentage(exam.getPassingPercentage())
                .selectedTopicCount(topicCount)
                .selectedTopics(selectedTopics)
                .createdDate(exam.getCreatedDate())
                .updatedDate(exam.getUpdatedDate())
                .updatedBy(exam.getUpdatedBy())
                .updatedByRole(exam.getUpdatedByRole())
                .build();
    }
}
