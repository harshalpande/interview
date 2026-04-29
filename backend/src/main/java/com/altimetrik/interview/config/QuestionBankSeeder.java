package com.altimetrik.interview.config;

import com.altimetrik.interview.entity.InterviewQuestionBank;
import com.altimetrik.interview.enums.TechnologySkill;
import com.altimetrik.interview.repository.InterviewQuestionBankRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class QuestionBankSeeder implements CommandLineRunner {

    private final InterviewQuestionBankRepository questionBankRepository;

    @Override
    public void run(String... args) {
        List<QuestionSeed> seeds = buildSeeds();
        int inserted = 0;
        for (QuestionSeed seed : seeds) {
            if (questionBankRepository.existsById(seed.id())) {
                continue;
            }
            questionBankRepository.save(toEntity(seed));
            inserted++;
        }
        if (inserted > 0) {
            log.info("Seeded {} interview question-bank entries.", inserted);
        }
    }

    private InterviewQuestionBank toEntity(QuestionSeed seed) {
        InterviewQuestionBank question = new InterviewQuestionBank();
        question.setId(seed.id());
        question.setTechnology(seed.technology());
        question.setDifficultyLevel(seed.difficultyLevel());
        question.setTitle(seed.title());
        question.setFilePath(seed.filePath());
        question.setDisplayName(seed.displayName());
        question.setProblemStatement(seed.problemStatement());
        question.setStarterCode(seed.starterCode());
        question.setReferenceSolution(seed.referenceSolution());
        question.setIdealDurationMinutes(idealDuration(seed.difficultyLevel()));
        question.setExpectedTimeComplexity(seed.expectedTimeComplexity());
        question.setExpectedSpaceComplexity(seed.expectedSpaceComplexity());
        question.setConcepts(String.join("\n", seed.concepts()));
        question.setEvaluationFocus("Correctness\nEdge cases\nReadable implementation");
        question.setActive(true);
        return question;
    }

    private int idealDuration(int difficultyLevel) {
        if (difficultyLevel >= 4) {
            return 15;
        }
        if (difficultyLevel >= 3) {
            return 12;
        }
        return 10;
    }

    private List<QuestionSeed> buildSeeds() {
        List<QuestionSeed> seeds = new ArrayList<>();
        addCharCounters(seeds);
        addArraySums(seeds);
        addStringPredicates(seeds);
        addStringTransforms(seeds);
        addArrayPredicates(seeds);
        return seeds;
    }

    private void addCharCounters(List<QuestionSeed> seeds) {
        List<CharCounter> specs = List.of(
                new CharCounter("vowels", "Vowel Counter", 1, "vowels", "is a vowel", "isVowel(ch)", "ch.lower() in \"aeiou\"", List.of("\"interview\"", "\"sky\"", "\"Education\""), List.of("4", "0", "5")),
                new CharCounter("digits", "Digit Counter", 1, "digits", "is a digit", "Character.isDigit(ch)", "ch.isdigit()", List.of("\"a1b22\"", "\"abc\"", "\"2026\""), List.of("3", "0", "4")),
                new CharCounter("uppercase", "Uppercase Letter Counter", 1, "uppercase letters", "is uppercase", "Character.isUpperCase(ch)", "ch.isupper()", List.of("\"JavaAPI\"", "\"lower\"", "\"A B C\""), List.of("4", "0", "3")),
                new CharCounter("lowercase", "Lowercase Letter Counter", 1, "lowercase letters", "is lowercase", "Character.isLowerCase(ch)", "ch.islower()", List.of("\"JavaAPI\"", "\"ABC\"", "\"a b c\""), List.of("3", "0", "3")),
                new CharCounter("spaces", "Space Counter", 1, "space characters", "is a space", "ch == ' '", "ch == ' '", List.of("\"hello world\"", "\"nospace\"", "\"a b c\""), List.of("1", "0", "2")),
                new CharCounter("letters", "Letter Counter", 2, "letters", "is alphabetic", "Character.isLetter(ch)", "ch.isalpha()", List.of("\"abc123\"", "\"2026\"", "\"A b!\""), List.of("3", "0", "2")),
                new CharCounter("nonalnum", "Non Alphanumeric Counter", 2, "non-alphanumeric characters", "is not a letter or digit", "!Character.isLetterOrDigit(ch)", "not ch.isalnum()", List.of("\"a-b!\"", "\"abc123\"", "\"x y\""), List.of("2", "0", "1")),
                new CharCounter("consonants", "Consonant Counter", 2, "consonants", "is an English consonant", "Character.isLetter(ch) && !isVowel(ch)", "ch.isalpha() and ch.lower() not in \"aeiou\"", List.of("\"banana\"", "\"aeiou\"", "\"Code\""), List.of("3", "0", "2")),
                new CharCounter("punctuation", "Punctuation Counter", 2, "punctuation characters", "is one of .,!?:;", "\".,!?:;\".indexOf(ch) >= 0", "ch in \".,!?:;\"", List.of("\"Hi, there!\"", "\"plain\"", "\"a:b;c\""), List.of("2", "0", "2")),
                new CharCounter("wordstarts", "Word Start Counter", 3, "word starts", "starts a new word", "i == 0 || text.charAt(i - 1) == ' '", "i == 0 or text[i - 1] == ' '", List.of("\"hello world\"", "\" single\"", "\"a b c\""), List.of("2", "1", "3"))
        );
        for (CharCounter spec : specs) {
            seeds.add(javaCharCounter(spec));
            seeds.add(pythonCharCounter(spec));
        }
    }

    private void addArraySums(List<QuestionSeed> seeds) {
        List<IntAggregator> specs = List.of(
                new IntAggregator("positive-sum", "Positive Sum", 1, "sum of all positive numbers", "n > 0", "n > 0", List.of("new int[]{1, -2, 3}", "new int[]{-5, -1}", "new int[]{0, 4}"), List.of("[1, -2, 3]", "[-5, -1]", "[0, 4]"), List.of("4", "0", "4")),
                new IntAggregator("even-sum", "Even Sum", 1, "sum of all even numbers", "n % 2 == 0", "n % 2 == 0", List.of("new int[]{1, 2, 4}", "new int[]{1, 3}", "new int[]{-2, 6}"), List.of("[1, 2, 4]", "[1, 3]", "[-2, 6]"), List.of("6", "0", "4")),
                new IntAggregator("odd-sum", "Odd Sum", 1, "sum of all odd numbers", "n % 2 != 0", "n % 2 != 0", List.of("new int[]{1, 2, 3}", "new int[]{2, 4}", "new int[]{-3, 5}"), List.of("[1, 2, 3]", "[2, 4]", "[-3, 5]"), List.of("4", "0", "2")),
                new IntAggregator("above-ten-sum", "Above Ten Sum", 2, "sum of numbers greater than 10", "n > 10", "n > 10", List.of("new int[]{8, 11, 20}", "new int[]{10, 9}", "new int[]{12, -1}"), List.of("[8, 11, 20]", "[10, 9]", "[12, -1]"), List.of("31", "0", "12")),
                new IntAggregator("negative-sum", "Negative Sum", 2, "sum of negative numbers", "n < 0", "n < 0", List.of("new int[]{-1, 2, -3}", "new int[]{1, 2}", "new int[]{-5, -5}"), List.of("[-1, 2, -3]", "[1, 2]", "[-5, -5]"), List.of("-4", "0", "-10")),
                new IntAggregator("multiple-three-sum", "Multiples Of Three Sum", 2, "sum of multiples of 3", "n % 3 == 0", "n % 3 == 0", List.of("new int[]{3, 4, 6}", "new int[]{1, 2}", "new int[]{-3, 9}"), List.of("[3, 4, 6]", "[1, 2]", "[-3, 9]"), List.of("9", "0", "6")),
                new IntAggregator("single-digit-sum", "Single Digit Sum", 2, "sum of numbers from -9 to 9", "Math.abs(n) < 10", "abs(n) < 10", List.of("new int[]{5, 10, -3}", "new int[]{11, 20}", "new int[]{-9, 9, 10}"), List.of("[5, 10, -3]", "[11, 20]", "[-9, 9, 10]"), List.of("2", "0", "0")),
                new IntAggregator("nonzero-sum", "Non Zero Sum", 1, "sum of non-zero numbers", "n != 0", "n != 0", List.of("new int[]{0, 2, 3}", "new int[]{0, 0}", "new int[]{-1, 1}"), List.of("[0, 2, 3]", "[0, 0]", "[-1, 1]"), List.of("5", "0", "0")),
                new IntAggregator("square-small-sum", "Small Square Sum", 3, "sum of squares for values whose absolute value is at most 5", "Math.abs(n) <= 5", "abs(n) <= 5", List.of("new int[]{2, 6, -3}", "new int[]{7}", "new int[]{5, -5}"), List.of("[2, 6, -3]", "[7]", "[5, -5]"), List.of("13", "0", "50")),
                new IntAggregator("index-even-sum", "Even Index Sum", 3, "sum of numbers at even indexes", "i % 2 == 0", "i % 2 == 0", List.of("new int[]{5, 1, 7, 2}", "new int[]{9}", "new int[]{}"), List.of("[5, 1, 7, 2]", "[9]", "[]"), List.of("12", "9", "0"))
        );
        for (IntAggregator spec : specs) {
            seeds.add(javaIntAggregator(spec));
            seeds.add(pythonIntAggregator(spec));
        }
    }

    private void addStringPredicates(List<QuestionSeed> seeds) {
        List<StringPredicate> specs = List.of(
                new StringPredicate("palindrome", "Palindrome Check", 2, "returns true when the input reads the same forward and backward", "int left = 0;\n        int right = text.length() - 1;\n        while (left < right) {\n            if (text.charAt(left++) != text.charAt(right--)) {\n                return false;\n            }\n        }\n        return true;", "return text == text[::-1]", List.of("\"level\"", "\"java\"", "\"\""), List.of("\"level\"", "\"java\"", "\"\""), List.of("true", "false", "true")),
                new StringPredicate("unique-chars", "Unique Character Check", 3, "returns true when every character appears only once", "java.util.Set<Character> seen = new java.util.HashSet<>();\n        for (int i = 0; i < text.length(); i++) {\n            if (!seen.add(text.charAt(i))) {\n                return false;\n            }\n        }\n        return true;", "return len(set(text)) == len(text)", List.of("\"abc\"", "\"hello\"", "\"\""), List.of("\"abc\"", "\"hello\"", "\"\""), List.of("true", "false", "true")),
                new StringPredicate("has-digit", "Digit Presence Check", 1, "returns true when the input contains at least one digit", "for (int i = 0; i < text.length(); i++) {\n            if (Character.isDigit(text.charAt(i))) {\n                return true;\n            }\n        }\n        return false;", "return any(ch.isdigit() for ch in text)", List.of("\"abc1\"", "\"abc\"", "\"7\""), List.of("\"abc1\"", "\"abc\"", "\"7\""), List.of("true", "false", "true")),
                new StringPredicate("only-letters", "Only Letters Check", 2, "returns true when every character is a letter and the string is not empty", "if (text.isEmpty()) {\n            return false;\n        }\n        for (int i = 0; i < text.length(); i++) {\n            if (!Character.isLetter(text.charAt(i))) {\n                return false;\n            }\n        }\n        return true;", "return bool(text) and all(ch.isalpha() for ch in text)", List.of("\"abc\"", "\"abc1\"", "\"\""), List.of("\"abc\"", "\"abc1\"", "\"\""), List.of("true", "false", "false")),
                new StringPredicate("balanced-parens", "Balanced Parentheses Check", 3, "returns true when parentheses are balanced; ignore non-parenthesis characters", "int balance = 0;\n        for (int i = 0; i < text.length(); i++) {\n            char ch = text.charAt(i);\n            if (ch == '(') {\n                balance++;\n            } else if (ch == ')') {\n                balance--;\n                if (balance < 0) {\n                    return false;\n                }\n            }\n        }\n        return balance == 0;", "balance = 0\n    for ch in text:\n        if ch == '(':\n            balance += 1\n        elif ch == ')':\n            balance -= 1\n            if balance < 0:\n                return False\n    return balance == 0", List.of("\"(a)\"", "\"(()\"", "\")(\""), List.of("\"(a)\"", "\"(()\"", "\")(\""), List.of("true", "false", "false")),
                new StringPredicate("alternating-case", "Alternating Case Check", 4, "returns true when alphabetic characters alternate between lower and upper case", "Boolean previousLower = null;\n        for (int i = 0; i < text.length(); i++) {\n            char ch = text.charAt(i);\n            if (!Character.isLetter(ch)) {\n                continue;\n            }\n            boolean currentLower = Character.isLowerCase(ch);\n            if (previousLower != null && previousLower == currentLower) {\n                return false;\n            }\n            previousLower = currentLower;\n        }\n        return previousLower != null;", "previous_lower = None\n    for ch in text:\n        if not ch.isalpha():\n            continue\n        current_lower = ch.islower()\n        if previous_lower is not None and previous_lower == current_lower:\n            return False\n        previous_lower = current_lower\n    return previous_lower is not None", List.of("\"aBcD\"", "\"AB\"", "\"1aB\""), List.of("\"aBcD\"", "\"AB\"", "\"1aB\""), List.of("true", "false", "true")),
                new StringPredicate("prefix-suffix", "Prefix Suffix Check", 2, "returns true when the first and last characters are the same and the string is not empty", "return !text.isEmpty() && text.charAt(0) == text.charAt(text.length() - 1);", "return bool(text) and text[0] == text[-1]", List.of("\"level\"", "\"java\"", "\"\""), List.of("\"level\"", "\"java\"", "\"\""), List.of("true", "false", "false")),
                new StringPredicate("two-vowels", "At Least Two Vowels Check", 2, "returns true when the string contains at least two vowels", "int count = 0;\n        for (int i = 0; i < text.length(); i++) {\n            if (\"aeiouAEIOU\".indexOf(text.charAt(i)) >= 0 && ++count >= 2) {\n                return true;\n            }\n        }\n        return false;", "count = 0\n    for ch in text:\n        if ch.lower() in 'aeiou':\n            count += 1\n            if count >= 2:\n                return True\n    return False", List.of("\"team\"", "\"sky\"", "\"Apple\""), List.of("\"team\"", "\"sky\"", "\"Apple\""), List.of("true", "false", "true")),
                new StringPredicate("camel-token", "Camel Token Check", 4, "returns true when the token starts lowercase and contains at least one uppercase letter", "if (text.isEmpty() || !Character.isLowerCase(text.charAt(0))) {\n            return false;\n        }\n        for (int i = 1; i < text.length(); i++) {\n            if (Character.isUpperCase(text.charAt(i))) {\n                return true;\n            }\n        }\n        return false;", "return bool(text) and text[0].islower() and any(ch.isupper() for ch in text[1:])", List.of("\"userName\"", "\"Username\"", "\"user\""), List.of("\"userName\"", "\"Username\"", "\"user\""), List.of("true", "false", "false")),
                new StringPredicate("valid-binary", "Binary String Check", 1, "returns true when every character is either 0 or 1 and the string is not empty", "if (text.isEmpty()) {\n            return false;\n        }\n        for (int i = 0; i < text.length(); i++) {\n            char ch = text.charAt(i);\n            if (ch != '0' && ch != '1') {\n                return false;\n            }\n        }\n        return true;", "return bool(text) and all(ch in '01' for ch in text)", List.of("\"1010\"", "\"102\"", "\"\""), List.of("\"1010\"", "\"102\"", "\"\""), List.of("true", "false", "false"))
        );
        for (StringPredicate spec : specs) {
            seeds.add(javaStringPredicate(spec));
            seeds.add(pythonStringPredicate(spec));
        }
    }

    private void addStringTransforms(List<QuestionSeed> seeds) {
        List<StringTransform> specs = List.of(
                new StringTransform("remove-spaces", "Remove Spaces", 1, "returns the input without space characters", "if (ch != ' ') { builder.append(ch); }", "if ch != ' '", List.of("\"a b c\"", "\"space\"", "\"  x\""), List.of("\"a b c\"", "\"space\"", "\"  x\""), List.of("\"abc\"", "\"space\"", "\"x\"")),
                new StringTransform("only-digits", "Keep Digits", 1, "returns a string containing only digits from the input", "if (Character.isDigit(ch)) { builder.append(ch); }", "if ch.isdigit()", List.of("\"a1b2\"", "\"abc\"", "\"2026\""), List.of("\"a1b2\"", "\"abc\"", "\"2026\""), List.of("\"12\"", "\"\"", "\"2026\"")),
                new StringTransform("mask-vowels", "Mask Vowels", 2, "returns the input with every vowel replaced by *", "builder.append(\"aeiouAEIOU\".indexOf(ch) >= 0 ? '*' : ch);", "None", List.of("\"hello\"", "\"sky\"", "\"AE\""), List.of("\"hello\"", "\"sky\"", "\"AE\""), List.of("\"h*ll*\"", "\"sky\"", "\"**\"")),
                new StringTransform("reverse-string", "Reverse String", 1, "returns the reversed input string", "builder.insert(0, ch);", "None", List.of("\"abc\"", "\"a\"", "\"\""), List.of("\"abc\"", "\"a\"", "\"\""), List.of("\"cba\"", "\"a\"", "\"\"")),
                new StringTransform("compress-dupes", "Collapse Adjacent Duplicates", 4, "returns a string where repeated adjacent characters are collapsed to one occurrence", "if (builder.length() == 0 || builder.charAt(builder.length() - 1) != ch) { builder.append(ch); }", "None", List.of("\"aaabbc\"", "\"abc\"", "\"\""), List.of("\"aaabbc\"", "\"abc\"", "\"\""), List.of("\"abc\"", "\"abc\"", "\"\"")),
                new StringTransform("uppercase-letters", "Uppercase Letters", 1, "returns the input with alphabetic characters converted to uppercase", "builder.append(Character.toUpperCase(ch));", "None", List.of("\"Java 17\"", "\"ok\"", "\"\""), List.of("\"Java 17\"", "\"ok\"", "\"\""), List.of("\"JAVA 17\"", "\"OK\"", "\"\"")),
                new StringTransform("remove-digits", "Remove Digits", 2, "returns the input without digit characters", "if (!Character.isDigit(ch)) { builder.append(ch); }", "if not ch.isdigit()", List.of("\"a1b2\"", "\"abc\"", "\"123\""), List.of("\"a1b2\"", "\"abc\"", "\"123\""), List.of("\"ab\"", "\"abc\"", "\"\"")),
                new StringTransform("double-letters", "Double Letters", 3, "returns a string where every letter is repeated twice and non-letters remain once", "builder.append(ch); if (Character.isLetter(ch)) { builder.append(ch); }", "None", List.of("\"a1B\"", "\"!\"", "\"ab\""), List.of("\"a1B\"", "\"!\"", "\"ab\""), List.of("\"aa1BB\"", "\"!\"", "\"aabb\"")),
                new StringTransform("trim-edges", "Manual Trim", 3, "returns the string without leading or trailing spaces", "manualTrim", "manualTrim", List.of("\"  hi  \"", "\"x\"", "\"   \""), List.of("\"  hi  \"", "\"x\"", "\"   \""), List.of("\"hi\"", "\"x\"", "\"\"")),
                new StringTransform("initials", "Initials From Words", 4, "returns uppercase initials from words separated by spaces", "initials", "initials", List.of("\"java developer\"", "\"single\"", "\"\""), List.of("\"java developer\"", "\"single\"", "\"\""), List.of("\"JD\"", "\"S\"", "\"\""))
        );
        for (StringTransform spec : specs) {
            seeds.add(javaStringTransform(spec));
            seeds.add(pythonStringTransform(spec));
        }
    }

    private void addArrayPredicates(List<QuestionSeed> seeds) {
        List<ArrayPredicate> specs = List.of(
                new ArrayPredicate("strict-increasing", "Strictly Increasing Check", 2, "returns true when every value is greater than the previous one", "nums[i] <= nums[i - 1]", "nums[i] <= nums[i - 1]", List.of("new int[]{1, 2, 3}", "new int[]{1, 1}", "new int[]{}"), List.of("[1, 2, 3]", "[1, 1]", "[]"), List.of("true", "false", "true")),
                new ArrayPredicate("contains-duplicate", "Duplicate Check", 3, "returns true when any value appears more than once", "duplicate", "duplicate", List.of("new int[]{1, 2, 1}", "new int[]{1, 2}", "new int[]{}"), List.of("[1, 2, 1]", "[1, 2]", "[]"), List.of("true", "false", "false")),
                new ArrayPredicate("all-positive", "All Positive Check", 1, "returns true when every value is greater than zero", "nums[i] <= 0", "nums[i] <= 0", List.of("new int[]{1, 2}", "new int[]{1, 0}", "new int[]{}"), List.of("[1, 2]", "[1, 0]", "[]"), List.of("true", "false", "true")),
                new ArrayPredicate("has-pair-sum-zero", "Zero Pair Check", 4, "returns true when two different values sum to zero", "pairZero", "pairZero", List.of("new int[]{3, -3, 5}", "new int[]{1, 2}", "new int[]{0}"), List.of("[3, -3, 5]", "[1, 2]", "[0]"), List.of("true", "false", "false")),
                new ArrayPredicate("alternating-parity", "Alternating Parity Check", 3, "returns true when adjacent values alternate between even and odd", "Math.abs(nums[i] % 2) == Math.abs(nums[i - 1] % 2)", "abs(nums[i] % 2) == abs(nums[i - 1] % 2)", List.of("new int[]{1, 2, 3}", "new int[]{2, 4}", "new int[]{5}"), List.of("[1, 2, 3]", "[2, 4]", "[5]"), List.of("true", "false", "true")),
                new ArrayPredicate("has-majority-positive", "Majority Positive Check", 2, "returns true when more than half the values are positive", "majorityPositive", "majorityPositive", List.of("new int[]{1, -1, 2}", "new int[]{1, -1}", "new int[]{}"), List.of("[1, -1, 2]", "[1, -1]", "[]"), List.of("true", "false", "false")),
                new ArrayPredicate("bounded-difference", "Bounded Adjacent Difference Check", 4, "returns true when every adjacent difference is at most 3", "Math.abs(nums[i] - nums[i - 1]) > 3", "abs(nums[i] - nums[i - 1]) > 3", List.of("new int[]{1, 3, 6}", "new int[]{1, 5}", "new int[]{}"), List.of("[1, 3, 6]", "[1, 5]", "[]"), List.of("true", "false", "true")),
                new ArrayPredicate("all-even", "All Even Check", 1, "returns true when every value is even", "nums[i] % 2 != 0", "nums[i] % 2 != 0", List.of("new int[]{2, 4}", "new int[]{2, 3}", "new int[]{}"), List.of("[2, 4]", "[2, 3]", "[]"), List.of("true", "false", "true")),
                new ArrayPredicate("peak-exists", "Peak Element Exists Check", 5, "returns true when an element is greater than both immediate neighbors", "peak", "peak", List.of("new int[]{1, 3, 2}", "new int[]{1, 2, 3}", "new int[]{1, 2}"), List.of("[1, 3, 2]", "[1, 2, 3]", "[1, 2]"), List.of("true", "false", "false")),
                new ArrayPredicate("can-split-equal-sum", "Equal Prefix Suffix Split Check", 5, "returns true when the array can be split into two non-empty parts with equal sum", "equalSplit", "equalSplit", List.of("new int[]{1, 1, 2}", "new int[]{1, 2, 3}", "new int[]{2}"), List.of("[1, 1, 2]", "[1, 2, 3]", "[2]"), List.of("true", "true", "false"))
        );
        for (ArrayPredicate spec : specs) {
            seeds.add(javaArrayPredicate(spec));
            seeds.add(pythonArrayPredicate(spec));
        }
    }

    private QuestionSeed javaCharCounter(CharCounter spec) {
        String method = "count" + pascal(spec.id());
        String problem = "Implement `" + method + "` to count how many characters in the non-null string `text` are " + spec.description() + ".";
        String helper = spec.id().equals("vowels") || spec.id().equals("consonants")
                ? "\n    private static boolean isVowel(char ch) {\n        return \"aeiouAEIOU\".indexOf(ch) >= 0;\n    }\n"
                : "";
        String body = spec.id().equals("wordstarts")
                ? "int count = 0;\n        for (int i = 0; i < text.length(); i++) {\n            char ch = text.charAt(i);\n            if (ch != ' ' && (" + spec.javaCondition() + ")) {\n                count++;\n            }\n        }\n        return count;"
                : "int count = 0;\n        for (int i = 0; i < text.length(); i++) {\n            char ch = text.charAt(i);\n            if (" + spec.javaCondition() + ") {\n                count++;\n            }\n        }\n        return count;";
        String starter = javaClass("int", method, "String text", "// TODO: implement\n        return 0;", helper, javaAssertInt(method, spec.inputs(), spec.expected()));
        String reference = javaClass("int", method, "String text", body, helper, "");
        return seed("java-char-" + spec.id(), TechnologySkill.JAVA, spec.difficultyLevel(), spec.title(), "Question1.java", problem, starter, reference, "O(n)", "O(1)", List.of("strings", "iteration"));
    }

    private QuestionSeed pythonCharCounter(CharCounter spec) {
        String function = "count_" + snake(spec.id());
        String problem = "Implement `" + function + "` to count how many characters in the non-null string `text` are " + spec.description() + ".";
        String body = spec.id().equals("wordstarts")
                ? "count = 0\n    for i, ch in enumerate(text):\n        if ch != ' ' and (" + spec.pythonCondition() + "):\n            count += 1\n    return count"
                : "count = 0\n    for ch in text:\n        if " + spec.pythonCondition() + ":\n            count += 1\n    return count";
        String starter = pythonFunction(function, "text", "# TODO: implement\n    return 0", pythonAssert(function, spec.inputs(), spec.expected()));
        String reference = pythonFunction(function, "text", body, "");
        return seed("python-char-" + spec.id(), TechnologySkill.PYTHON, spec.difficultyLevel(), spec.title(), "question-1.py", problem, starter, reference, "O(n)", "O(1)", List.of("strings", "iteration"));
    }

    private QuestionSeed javaIntAggregator(IntAggregator spec) {
        String method = camel(spec.id());
        String problem = "Implement `" + method + "` to return the " + spec.description() + " from the integer array `nums`.";
        String body = spec.id().equals("square-small-sum")
                ? "int total = 0;\n        for (int n : nums) {\n            if (" + spec.javaCondition() + ") {\n                total += n * n;\n            }\n        }\n        return total;"
                : spec.id().equals("index-even-sum")
                ? "int total = 0;\n        for (int i = 0; i < nums.length; i++) {\n            int n = nums[i];\n            if (" + spec.javaCondition() + ") {\n                total += n;\n            }\n        }\n        return total;"
                : "int total = 0;\n        for (int n : nums) {\n            if (" + spec.javaCondition() + ") {\n                total += n;\n            }\n        }\n        return total;";
        String starter = javaClass("int", method, "int[] nums", "// TODO: implement\n        return 0;", "", javaAssertInt(method, spec.javaInputs(), spec.expected()));
        String reference = javaClass("int", method, "int[] nums", body, "", "");
        return seed("java-array-sum-" + spec.id(), TechnologySkill.JAVA, spec.difficultyLevel(), spec.title(), "Question1.java", problem, starter, reference, "O(n)", "O(1)", List.of("arrays", "iteration"));
    }

    private QuestionSeed pythonIntAggregator(IntAggregator spec) {
        String function = snake(spec.id());
        String problem = "Implement `" + function + "` to return the " + spec.description() + " from the list `nums`.";
        String body = spec.id().equals("square-small-sum")
                ? "total = 0\n    for n in nums:\n        if " + spec.pythonCondition() + ":\n            total += n * n\n    return total"
                : spec.id().equals("index-even-sum")
                ? "total = 0\n    for i, n in enumerate(nums):\n        if " + spec.pythonCondition() + ":\n            total += n\n    return total"
                : "total = 0\n    for n in nums:\n        if " + spec.pythonCondition() + ":\n            total += n\n    return total";
        String starter = pythonFunction(function, "nums", "# TODO: implement\n    return 0", pythonAssert(function, spec.pythonInputs(), spec.expected()));
        String reference = pythonFunction(function, "nums", body, "");
        return seed("python-array-sum-" + spec.id(), TechnologySkill.PYTHON, spec.difficultyLevel(), spec.title(), "question-1.py", problem, starter, reference, "O(n)", "O(1)", List.of("lists", "iteration"));
    }

    private QuestionSeed javaStringPredicate(StringPredicate spec) {
        String method = "is" + pascal(spec.id());
        String problem = "Implement `" + method + "` so it " + spec.description() + ".";
        String starter = javaClass("boolean", method, "String text", "// TODO: implement\n        return false;", "", javaAssertBool(method, spec.javaInputs(), spec.expected()));
        String reference = javaClass("boolean", method, "String text", spec.javaBody(), "", "");
        return seed("java-string-predicate-" + spec.id(), TechnologySkill.JAVA, spec.difficultyLevel(), spec.title(), "Question1.java", problem, starter, reference, "O(n)", spec.id().equals("unique-chars") ? "O(n)" : "O(1)", List.of("strings", "conditionals"));
    }

    private QuestionSeed pythonStringPredicate(StringPredicate spec) {
        String function = "is_" + snake(spec.id());
        String problem = "Implement `" + function + "` so it " + spec.description() + ".";
        String starter = pythonFunction(function, "text", "# TODO: implement\n    return False", pythonAssert(function, spec.pythonInputs(), spec.expected()));
        String reference = pythonFunction(function, "text", spec.pythonBody(), "");
        return seed("python-string-predicate-" + spec.id(), TechnologySkill.PYTHON, spec.difficultyLevel(), spec.title(), "question-1.py", problem, starter, reference, "O(n)", spec.id().equals("unique-chars") ? "O(n)" : "O(1)", List.of("strings", "conditionals"));
    }

    private QuestionSeed javaStringTransform(StringTransform spec) {
        String method = camel(spec.id());
        String problem = "Implement `" + method + "` so it " + spec.description() + ".";
        String body = javaTransformBody(spec);
        String starter = javaClass("String", method, "String text", "// TODO: implement\n        return \"\";", "", javaAssertString(method, spec.javaInputs(), spec.expected()));
        String reference = javaClass("String", method, "String text", body, "", "");
        return seed("java-string-transform-" + spec.id(), TechnologySkill.JAVA, spec.difficultyLevel(), spec.title(), "Question1.java", problem, starter, reference, "O(n)", "O(n)", List.of("strings", "transformation"));
    }

    private QuestionSeed pythonStringTransform(StringTransform spec) {
        String function = snake(spec.id());
        String problem = "Implement `" + function + "` so it " + spec.description() + ".";
        String body = pythonTransformBody(spec);
        String starter = pythonFunction(function, "text", "# TODO: implement\n    return \"\"", pythonAssert(function, spec.pythonInputs(), spec.expected()));
        String reference = pythonFunction(function, "text", body, "");
        return seed("python-string-transform-" + spec.id(), TechnologySkill.PYTHON, spec.difficultyLevel(), spec.title(), "question-1.py", problem, starter, reference, "O(n)", "O(n)", List.of("strings", "transformation"));
    }

    private QuestionSeed javaArrayPredicate(ArrayPredicate spec) {
        String method = "has" + pascal(spec.id());
        String problem = "Implement `" + method + "` so it " + spec.description() + ".";
        String starter = javaClass("boolean", method, "int[] nums", "// TODO: implement\n        return false;", "", javaAssertBool(method, spec.javaInputs(), spec.expected()));
        String reference = javaClass("boolean", method, "int[] nums", javaArrayPredicateBody(spec), "", "");
        String space = List.of("duplicate", "pairZero").contains(spec.javaMode()) ? "O(n)" : "O(1)";
        return seed("java-array-predicate-" + spec.id(), TechnologySkill.JAVA, spec.difficultyLevel(), spec.title(), "Question1.java", problem, starter, reference, "O(n)", space, List.of("arrays", "conditionals"));
    }

    private QuestionSeed pythonArrayPredicate(ArrayPredicate spec) {
        String function = "has_" + snake(spec.id());
        String problem = "Implement `" + function + "` so it " + spec.description() + ".";
        String starter = pythonFunction(function, "nums", "# TODO: implement\n    return False", pythonAssert(function, spec.pythonInputs(), spec.expected()));
        String reference = pythonFunction(function, "nums", pythonArrayPredicateBody(spec), "");
        String space = List.of("duplicate", "pairZero").contains(spec.pythonMode()) ? "O(n)" : "O(1)";
        return seed("python-array-predicate-" + spec.id(), TechnologySkill.PYTHON, spec.difficultyLevel(), spec.title(), "question-1.py", problem, starter, reference, "O(n)", space, List.of("lists", "conditionals"));
    }

    private String javaTransformBody(StringTransform spec) {
        if ("manualTrim".equals(spec.javaMode())) {
            return "int start = 0;\n        int end = text.length() - 1;\n        while (start <= end && text.charAt(start) == ' ') {\n            start++;\n        }\n        while (end >= start && text.charAt(end) == ' ') {\n            end--;\n        }\n        return text.substring(start, end + 1);";
        }
        if ("initials".equals(spec.javaMode())) {
            return "StringBuilder builder = new StringBuilder();\n        boolean atWordStart = true;\n        for (int i = 0; i < text.length(); i++) {\n            char ch = text.charAt(i);\n            if (ch == ' ') {\n                atWordStart = true;\n            } else if (atWordStart) {\n                builder.append(Character.toUpperCase(ch));\n                atWordStart = false;\n            }\n        }\n        return builder.toString();";
        }
        if ("None".equals(spec.javaMode())) {
            if (spec.id().equals("reverse-string")) {
                return "StringBuilder builder = new StringBuilder();\n        for (int i = 0; i < text.length(); i++) {\n            char ch = text.charAt(i);\n            builder.insert(0, ch);\n        }\n        return builder.toString();";
            }
            if (spec.id().equals("mask-vowels")) {
                return "StringBuilder builder = new StringBuilder();\n        for (int i = 0; i < text.length(); i++) {\n            char ch = text.charAt(i);\n            builder.append(\"aeiouAEIOU\".indexOf(ch) >= 0 ? '*' : ch);\n        }\n        return builder.toString();";
            }
        }
        return "StringBuilder builder = new StringBuilder();\n        for (int i = 0; i < text.length(); i++) {\n            char ch = text.charAt(i);\n            " + spec.javaMode() + "\n        }\n        return builder.toString();";
    }

    private String pythonTransformBody(StringTransform spec) {
        if ("manualTrim".equals(spec.pythonMode())) {
            return "start = 0\n    end = len(text) - 1\n    while start <= end and text[start] == ' ':\n        start += 1\n    while end >= start and text[end] == ' ':\n        end -= 1\n    return text[start:end + 1]";
        }
        if ("initials".equals(spec.pythonMode())) {
            return "result = []\n    at_word_start = True\n    for ch in text:\n        if ch == ' ':\n            at_word_start = True\n        elif at_word_start:\n            result.append(ch.upper())\n            at_word_start = False\n    return ''.join(result)";
        }
        if ("None".equals(spec.pythonMode())) {
            if (spec.id().equals("reverse-string")) {
                return "return text[::-1]";
            }
            if (spec.id().equals("mask-vowels")) {
                return "return ''.join('*' if ch.lower() in 'aeiou' else ch for ch in text)";
            }
            if (spec.id().equals("uppercase-letters")) {
                return "return text.upper()";
            }
            if (spec.id().equals("double-letters")) {
                return "result = []\n    for ch in text:\n        result.append(ch)\n        if ch.isalpha():\n            result.append(ch)\n    return ''.join(result)";
            }
            if (spec.id().equals("compress-dupes")) {
                return "result = []\n    for ch in text:\n        if not result or result[-1] != ch:\n            result.append(ch)\n    return ''.join(result)";
            }
        }
        return "result = []\n    for ch in text:\n        if " + spec.pythonMode() + ":\n            result.append(ch)\n    return ''.join(result)";
    }

    private String javaArrayPredicateBody(ArrayPredicate spec) {
        return switch (spec.javaMode()) {
            case "duplicate" -> "java.util.Set<Integer> seen = new java.util.HashSet<>();\n        for (int n : nums) {\n            if (!seen.add(n)) {\n                return true;\n            }\n        }\n        return false;";
            case "pairZero" -> "java.util.Set<Integer> seen = new java.util.HashSet<>();\n        for (int n : nums) {\n            if (seen.contains(-n)) {\n                return true;\n            }\n            seen.add(n);\n        }\n        return false;";
            case "majorityPositive" -> "int positives = 0;\n        for (int n : nums) {\n            if (n > 0) {\n                positives++;\n            }\n        }\n        return positives > nums.length / 2;";
            case "peak" -> "for (int i = 1; i < nums.length - 1; i++) {\n            if (nums[i] > nums[i - 1] && nums[i] > nums[i + 1]) {\n                return true;\n            }\n        }\n        return false;";
            case "equalSplit" -> "if (nums.length < 2) {\n            return false;\n        }\n        int total = 0;\n        for (int n : nums) {\n            total += n;\n        }\n        int left = 0;\n        for (int i = 0; i < nums.length - 1; i++) {\n            left += nums[i];\n            if (left == total - left) {\n                return true;\n            }\n        }\n        return false;";
            default -> "for (int i = 1; i < nums.length; i++) {\n            if (" + spec.javaMode() + ") {\n                return false;\n            }\n        }\n        return true;";
        };
    }

    private String pythonArrayPredicateBody(ArrayPredicate spec) {
        return switch (spec.pythonMode()) {
            case "duplicate" -> "seen = set()\n    for n in nums:\n        if n in seen:\n            return True\n        seen.add(n)\n    return False";
            case "pairZero" -> "seen = set()\n    for n in nums:\n        if -n in seen:\n            return True\n        seen.add(n)\n    return False";
            case "majorityPositive" -> "return sum(1 for n in nums if n > 0) > len(nums) / 2";
            case "peak" -> "for i in range(1, len(nums) - 1):\n        if nums[i] > nums[i - 1] and nums[i] > nums[i + 1]:\n            return True\n    return False";
            case "equalSplit" -> "if len(nums) < 2:\n        return False\n    total = sum(nums)\n    left = 0\n    for i in range(len(nums) - 1):\n        left += nums[i]\n        if left == total - left:\n            return True\n    return False";
            default -> "for i in range(1, len(nums)):\n        if " + spec.pythonMode() + ":\n            return False\n    return True";
        };
    }

    private String javaClass(String returnType, String method, String params, String body, String helper, String assertions) {
        return """
                import org.junit.Assert;

                public class Main {
                    public static %s %s(%s) {
                        %s
                    }
                %s
                    public static void main(String[] args) {
                %s
                        System.out.println("All assertions passed");
                    }
                }
                """.formatted(returnType, method, params, indent(body, 2), helper == null ? "" : helper, assertions);
    }

    private String pythonFunction(String function, String params, String body, String assertions) {
        return """
                def %s(%s):
                    %s


                def main():
                %s
                    print("All assertions passed")


                if __name__ == "__main__":
                    main()
                """.formatted(function, params, indent(body, 1), assertions);
    }

    private String javaAssertInt(String method, List<String> inputs, List<String> expected) {
        return javaAssertions("Assert.assertEquals(%s, %s(%s));", method, inputs, expected);
    }

    private String javaAssertBool(String method, List<String> inputs, List<String> expected) {
        return javaAssertions("Assert.assertEquals(%s, %s(%s));", method, inputs, expected);
    }

    private String javaAssertString(String method, List<String> inputs, List<String> expected) {
        return javaAssertions("Assert.assertEquals(%s, %s(%s));", method, inputs, expected);
    }

    private String javaAssertions(String format, String method, List<String> inputs, List<String> expected) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < inputs.size(); i++) {
            builder.append("        ").append(format.formatted(expected.get(i), method, inputs.get(i))).append("\n");
        }
        return builder.toString();
    }

    private String pythonAssert(String function, List<String> inputs, List<String> expected) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < inputs.size(); i++) {
            builder.append("    assert ").append(function).append("(").append(inputs.get(i)).append(") == ").append(expected.get(i)).append("\n");
        }
        return builder.toString();
    }

    private QuestionSeed seed(String id,
                              TechnologySkill technology,
                              int difficultyLevel,
                              String title,
                              String filePath,
                              String problemStatement,
                              String starterCode,
                              String referenceSolution,
                              String expectedTimeComplexity,
                              String expectedSpaceComplexity,
                              List<String> concepts) {
        return new QuestionSeed(id, technology, difficultyLevel, title, filePath, "Question 1", problemStatement, starterCode,
                referenceSolution, expectedTimeComplexity, expectedSpaceComplexity, concepts);
    }

    private String camel(String value) {
        String pascal = pascal(value);
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    private String pascal(String value) {
        StringBuilder builder = new StringBuilder();
        for (String part : value.split("-")) {
            if (!part.isBlank()) {
                builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return builder.toString();
    }

    private String snake(String value) {
        return value.replace('-', '_');
    }

    private String indent(String value, int levels) {
        String prefix = "    ".repeat(levels);
        return value.replace("\n", "\n" + prefix);
    }

    private record QuestionSeed(String id,
                                TechnologySkill technology,
                                int difficultyLevel,
                                String title,
                                String filePath,
                                String displayName,
                                String problemStatement,
                                String starterCode,
                                String referenceSolution,
                                String expectedTimeComplexity,
                                String expectedSpaceComplexity,
                                List<String> concepts) {
    }

    private record CharCounter(String id, String title, int difficultyLevel, String target, String description,
                               String javaCondition, String pythonCondition, List<String> inputs,
                               List<String> expected) {
    }

    private record IntAggregator(String id, String title, int difficultyLevel, String description,
                                 String javaCondition, String pythonCondition, List<String> javaInputs,
                                 List<String> pythonInputs, List<String> expected) {
    }

    private record StringPredicate(String id, String title, int difficultyLevel, String description,
                                   String javaBody, String pythonBody, List<String> javaInputs,
                                   List<String> pythonInputs, List<String> expected) {
    }

    private record StringTransform(String id, String title, int difficultyLevel, String description,
                                   String javaMode, String pythonMode, List<String> javaInputs,
                                   List<String> pythonInputs, List<String> expected) {
    }

    private record ArrayPredicate(String id, String title, int difficultyLevel, String description,
                                  String javaMode, String pythonMode, List<String> javaInputs,
                                  List<String> pythonInputs, List<String> expected) {
    }
}
