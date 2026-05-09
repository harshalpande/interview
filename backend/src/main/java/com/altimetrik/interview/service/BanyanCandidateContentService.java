package com.altimetrik.interview.service;

import com.altimetrik.interview.dto.AiQuestionResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class BanyanCandidateContentService {

    public AiQuestionResponse cleanseCandidateFacingResponse(AiQuestionResponse question) {
        if (question == null) {
            return null;
        }
        question.setStarterCode(cleanseCandidateStarterCode(question.getStarterCode()).sourceCode());
        return question;
    }

    public Optional<String> candidateFacingHintViolation(String problemStatement, String starterCode) {
        Optional<String> problemViolation = firstProblemStatementHintViolation(problemStatement);
        if (problemViolation.isPresent()) {
            return problemViolation;
        }
        return firstStarterCommentHintViolation(cleanseCandidateStarterCode(starterCode).removedComments());
    }

    public StarterCodeCleanseResult cleanseCandidateStarterCode(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return new StarterCodeCleanseResult("", "");
        }
        List<String> cleanedLines = new ArrayList<>();
        List<String> removedComments = new ArrayList<>();
        boolean inBlockComment = false;
        boolean leadingCommentSection = true;
        for (String rawLine : sourceCode.split("\\R", -1)) {
            String line = rawLine == null ? "" : rawLine;
            String trimmed = line.trim();

            if (leadingCommentSection && !inBlockComment && trimmed.isBlank()) {
                cleanedLines.add(rawLine);
                continue;
            }

            if (leadingCommentSection) {
                LeadingCommentLine leadingLine = preserveLeadingCommentLine(rawLine, inBlockComment);
                if (leadingLine.preserved()) {
                    cleanedLines.add(rawLine);
                    inBlockComment = leadingLine.inBlockComment();
                    continue;
                }
            }

            leadingCommentSection = false;
            CodeLineCleanseResult cleaned = stripCodeLineComments(rawLine, inBlockComment);
            inBlockComment = cleaned.inBlockComment();
            if (!cleaned.removedComment().isBlank()) {
                removedComments.add(cleaned.removedComment());
            }
            if (cleaned.content().isBlank() && !rawLine.isBlank()) {
                continue;
            }
            cleanedLines.add(cleaned.content());
        }
        return new StarterCodeCleanseResult(String.join("\n", cleanedLines).strip(), String.join("\n", removedComments));
    }

    private Optional<String> firstProblemStatementHintViolation(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeHintText(content);
        List<String> blockedPhrases = List.of(
                "hint",
                "bug",
                "defect",
                "wrong",
                "incorrect",
                "line ",
                "line-level",
                "bug is",
                "defect is",
                "fix the",
                "fix the failing",
                "failing assertion",
                "provided assertion",
                "provided assertions",
                "repair",
                "wrong update",
                "incorrect update",
                "solution direction"
        );
        return blockedPhrases.stream()
                .filter(normalized::contains)
                .findFirst()
                .map(phrase -> "problem statement contains '" + phrase + "'");
    }

    private Optional<String> firstStarterCommentHintViolation(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeHintText(content);
        List<String> blockedPhrases = List.of(
                "hint",
                "bug",
                "defect",
                "wrong",
                "incorrect",
                "repair",
                "failing",
                "fix the",
                "fix this",
                "line ",
                "strict bound",
                "strict bounds",
                "should ensure",
                "should be"
        );
        return blockedPhrases.stream()
                .filter(normalized::contains)
                .findFirst()
                .map(phrase -> "starter code comment contains '" + phrase + "'");
    }

    private String normalizeHintText(String content) {
        return content.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private LeadingCommentLine preserveLeadingCommentLine(String rawLine, boolean startsInBlockComment) {
        String trimmed = rawLine == null ? "" : rawLine.trim();
        if (startsInBlockComment) {
            return new LeadingCommentLine(true, !trimmed.contains("*/"));
        }
        if (trimmed.startsWith("//") || trimmed.startsWith("#")) {
            return new LeadingCommentLine(true, false);
        }
        if (trimmed.startsWith("/*")) {
            return new LeadingCommentLine(true, !trimmed.contains("*/"));
        }
        if (trimmed.startsWith("*")) {
            return new LeadingCommentLine(true, false);
        }
        return new LeadingCommentLine(false, false);
    }

    private CodeLineCleanseResult stripCodeLineComments(String rawLine, boolean startsInBlockComment) {
        if (rawLine == null) {
            return new CodeLineCleanseResult("", "", startsInBlockComment);
        }
        String line = rawLine;
        StringBuilder removed = new StringBuilder();
        boolean inBlock = startsInBlockComment;
        if (inBlock) {
            int blockEnd = line.indexOf("*/");
            if (blockEnd < 0) {
                return new CodeLineCleanseResult("", line, true);
            }
            removed.append(line, 0, blockEnd);
            line = line.substring(blockEnd + 2);
            inBlock = false;
        }
        while (true) {
            int blockStart = line.indexOf("/*");
            if (blockStart < 0) {
                break;
            }
            int blockEnd = line.indexOf("*/", blockStart + 2);
            if (blockEnd < 0) {
                appendRemovedComment(removed, line.substring(blockStart + 2));
                return new CodeLineCleanseResult(line.substring(0, blockStart).stripTrailing(), removed.toString(), true);
            }
            appendRemovedComment(removed, line.substring(blockStart + 2, blockEnd));
            line = line.substring(0, blockStart) + line.substring(blockEnd + 2);
        }
        LineCommentMatch lineComment = firstLineComment(line);
        if (lineComment.index() >= 0) {
            appendRemovedComment(removed, line.substring(lineComment.index() + lineComment.markerLength()));
            line = line.substring(0, lineComment.index());
        }
        return new CodeLineCleanseResult(line.stripTrailing(), removed.toString(), inBlock);
    }

    private LineCommentMatch firstLineComment(String line) {
        int slashComment = line.indexOf("//");
        int hashComment = line.indexOf("#");
        if (slashComment >= 0 && hashComment >= 0) {
            return slashComment < hashComment
                    ? new LineCommentMatch(slashComment, 2)
                    : new LineCommentMatch(hashComment, 1);
        }
        if (slashComment >= 0) {
            return new LineCommentMatch(slashComment, 2);
        }
        if (hashComment >= 0) {
            return new LineCommentMatch(hashComment, 1);
        }
        return new LineCommentMatch(-1, 0);
    }

    private void appendRemovedComment(StringBuilder removed, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!removed.isEmpty()) {
            removed.append('\n');
        }
        removed.append(value);
    }

    public record StarterCodeCleanseResult(String sourceCode, String removedComments) {
    }

    private record LeadingCommentLine(boolean preserved, boolean inBlockComment) {
    }

    private record CodeLineCleanseResult(String content, String removedComment, boolean inBlockComment) {
    }

    private record LineCommentMatch(int index, int markerLength) {
    }
}
