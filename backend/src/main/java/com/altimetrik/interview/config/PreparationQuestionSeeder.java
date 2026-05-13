package com.altimetrik.interview.config;

import com.altimetrik.interview.entity.InterviewQuestionBank;
import com.altimetrik.interview.entity.QuestionSeries;
import com.altimetrik.interview.enums.EvaluationStyle;
import com.altimetrik.interview.enums.QuestionSource;
import com.altimetrik.interview.enums.QuestionStarterType;
import com.altimetrik.interview.enums.TechnologySkill;
import com.altimetrik.interview.repository.InterviewQuestionBankRepository;
import com.altimetrik.interview.repository.QuestionSeriesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PreparationQuestionSeeder implements CommandLineRunner {

    private final QuestionSeriesRepository questionSeriesRepository;
    private final InterviewQuestionBankRepository questionBankRepository;

    @Override
    public void run(String... args) {
        int inserted = 0;
        for (BanyanSeed seed : seeds()) {
            if (!questionSeriesRepository.existsById(seed.seriesId())) {
                questionSeriesRepository.save(series(seed));
                inserted++;
            }
            for (BanyanLevel level : seed.levels()) {
                if (!questionBankRepository.existsById(level.id())) {
                    questionBankRepository.save(question(seed, level));
                    inserted++;
                }
            }
        }
        if (inserted > 0) {
            log.info("Seeded {} Preparation Mode Banyan entries.", inserted);
        }
    }

    private QuestionSeries series(BanyanSeed seed) {
        QuestionSeries series = new QuestionSeries();
        series.setId(seed.seriesId());
        series.setTitle(seed.title());
        series.setTechnology(seed.technology());
        series.setEvaluationStyle(EvaluationStyle.BANYAN);
        series.setExperienceBand(seed.experienceBand());
        series.setTargetRole(seed.targetRole());
        series.setProblemFamilyKey(seed.familyKey());
        series.setProblemFamilyDescription(seed.familyDescription());
        series.setSource(QuestionSource.SEEDED);
        series.setActive(true);
        return series;
    }

    private InterviewQuestionBank question(BanyanSeed seed, BanyanLevel level) {
        InterviewQuestionBank question = new InterviewQuestionBank();
        question.setId(level.id());
        question.setTechnology(seed.technology());
        question.setDifficultyLevel(level.sequence());
        question.setSeriesId(seed.seriesId());
        question.setSequenceNumber(level.sequence());
        question.setBanyanLevel(level.sequence());
        question.setEvaluationStyle(EvaluationStyle.BANYAN);
        question.setExperienceBand(seed.experienceBand());
        question.setTargetRole(seed.targetRole());
        question.setProblemFamilyKey(seed.familyKey());
        question.setStarterType(level.starterType());
        question.setSource(QuestionSource.SEEDED);
        question.setTitle(level.title());
        question.setFilePath(seed.technology() == TechnologySkill.PYTHON ? "banyan.py" : "Banyan.java");
        question.setDisplayName("Banyan Level " + level.sequence());
        question.setProblemStatement(level.problemStatement());
        question.setStarterCode(level.starterCode());
        question.setReferenceSolution(level.referenceSolution());
        question.setIdealDurationMinutes(20);
        question.setExpectedTimeComplexity("O(n)");
        question.setExpectedSpaceComplexity("O(1)");
        question.setConcepts(String.join("\n", List.of("banyan-family:" + seed.familyKey(), seed.familyDescription(), "preparation-mode", "assertion-based")));
        question.setEvaluationFocus("Assertions pass\nPrevious Banyan behavior preserved\nReadable implementation");
        question.setActive(true);
        return question;
    }

    private List<BanyanSeed> seeds() {
        return List.of(
                new BanyanSeed(
                        "prep-java-inventory-1-3",
                        TechnologySkill.JAVA,
                        "1-3",
                        "Java Developer",
                        "Inventory Stock Preparation",
                        "inventory-stock-management",
                        "stock availability and reorder rules",
                        List.of(
                                new BanyanLevel("prep-java-inventory-1-3-l1", 1, QuestionStarterType.BUG_FIX, "Fix Available Stock",
                                        "Complete the inventory stock calculation so it returns the available units after reservations are considered.",
                                        javaInventoryLevel1(false),
                                        javaInventoryLevel1(true)),
                                new BanyanLevel("prep-java-inventory-1-3-l2", 2, QuestionStarterType.EXTENSION, "Add Reorder Decision",
                                        "Extend the same inventory challenge. Preserve the stock calculation and complete the reorder decision for the configured reorder point.",
                                        javaInventoryLevel2(false),
                                        javaInventoryLevel2(true))
                        )
                ),
                new BanyanSeed(
                        "prep-python-inventory-1-3",
                        TechnologySkill.PYTHON,
                        "1-3",
                        "Python Developer",
                        "Inventory Stock Preparation",
                        "inventory-stock-management",
                        "stock availability and reorder rules",
                        List.of(
                                new BanyanLevel("prep-python-inventory-1-3-l1", 1, QuestionStarterType.BUG_FIX, "Fix Available Stock",
                                        "Complete the inventory stock calculation so it returns the available units after reservations are considered.",
                                        pythonInventoryLevel1(false),
                                        pythonInventoryLevel1(true)),
                                new BanyanLevel("prep-python-inventory-1-3-l2", 2, QuestionStarterType.EXTENSION, "Add Reorder Decision",
                                        "Extend the same inventory challenge. Preserve the stock calculation and complete the reorder decision for the configured reorder point.",
                                        pythonInventoryLevel2(false),
                                        pythonInventoryLevel2(true))
                        )
                )
        );
    }

    private String javaInventoryLevel1(boolean solved) {
        String body = solved ? "return totalUnits - reservedUnits;" : "return totalUnits + reservedUnits;";
        return """
                import org.junit.Assert;

                public class Main {
                    public static int availableStock(int totalUnits, int reservedUnits) {
                        %s
                    }

                    public static void main(String[] args) {
                        Assert.assertEquals(7, availableStock(10, 3));
                        Assert.assertEquals(0, availableStock(5, 5));
                        Assert.assertEquals(12, availableStock(12, 0));
                        System.out.println("All assertions passed");
                    }
                }
                """.formatted(body);
    }

    private String javaInventoryLevel2(boolean solved) {
        String body = "return totalUnits - reservedUnits;";
        String reorder = solved ? "return availableStock(totalUnits, reservedUnits) < reorderPoint;" : "// TODO: implement\n        return false;";
        return """
                import org.junit.Assert;

                public class Main {
                    public static int availableStock(int totalUnits, int reservedUnits) {
                        %s
                    }

                    public static boolean needsReorder(int totalUnits, int reservedUnits, int reorderPoint) {
                        %s
                    }

                    public static void main(String[] args) {
                        Assert.assertEquals(7, availableStock(10, 3));
                        Assert.assertEquals(0, availableStock(5, 5));
                        Assert.assertEquals(12, availableStock(12, 0));
                        Assert.assertEquals(true, needsReorder(10, 7, 5));
                        Assert.assertEquals(false, needsReorder(20, 4, 10));
                        System.out.println("All assertions passed");
                    }
                }
                """.formatted(body, reorder);
    }

    private String pythonInventoryLevel1(boolean solved) {
        String body = solved ? "return total_units - reserved_units" : "return total_units + reserved_units";
        return """
                def available_stock(total_units, reserved_units):
                    %s


                def main():
                    assert available_stock(10, 3) == 7
                    assert available_stock(5, 5) == 0
                    assert available_stock(12, 0) == 12
                    print("All assertions passed")


                if __name__ == "__main__":
                    main()
                """.formatted(body);
    }

    private String pythonInventoryLevel2(boolean solved) {
        String body = "return total_units - reserved_units";
        String reorder = solved ? "return available_stock(total_units, reserved_units) < reorder_point" : "# TODO: implement\n    return False";
        return """
                def available_stock(total_units, reserved_units):
                    %s


                def needs_reorder(total_units, reserved_units, reorder_point):
                    %s


                def main():
                    assert available_stock(10, 3) == 7
                    assert available_stock(5, 5) == 0
                    assert available_stock(12, 0) == 12
                    assert needs_reorder(10, 7, 5) is True
                    assert needs_reorder(20, 4, 10) is False
                    print("All assertions passed")


                if __name__ == "__main__":
                    main()
                """.formatted(body, reorder);
    }

    private record BanyanSeed(String seriesId,
                              TechnologySkill technology,
                              String experienceBand,
                              String targetRole,
                              String title,
                              String familyKey,
                              String familyDescription,
                              List<BanyanLevel> levels) {
    }

    private record BanyanLevel(String id,
                               int sequence,
                               QuestionStarterType starterType,
                               String title,
                               String problemStatement,
                               String starterCode,
                               String referenceSolution) {
    }
}
