package net.optionfactory.problems;

import java.util.Arrays;
import java.util.List;

public class Failure extends RuntimeException {

    public final List<Problem> problems;

    public Failure(List<Problem> problems, Throwable cause) {
        super("problems:" + problems, cause);
        this.problems = problems;
    }

    public Failure(List<Problem> problems, String reason) {
        super("problems (" + reason + "):" + problems);
        this.problems = problems;
    }

    public Failure(Problem problem, Throwable cause) {
        super("problems:" + Arrays.asList(problem.toString()), cause);
        this.problems = Arrays.asList(problem);
    }

    public Failure(Problem problem, String reason) {
        super("problems (" + reason + "):" + Arrays.asList(problem.toString()));
        this.problems = Arrays.asList(problem);
    }

    public Failure(List<Problem> problems) {
        super("problems:" + problems.toString());
        this.problems = problems;
    }

    public Failure(Problem problem) {
        super("problems:" + Arrays.asList(problem.toString()));
        this.problems = Arrays.asList(problem);
    }

    public static void enforce(List<Problem> problems) {
        if (problems.isEmpty()) {
            return;
        }
        throw new Failure(problems);
    }

    public static void enforce(List<Problem> problems, String reason) {
        if (problems.isEmpty()) {
            return;
        }
        throw new Failure(problems, reason);
    }

    public static void enforce(List<Problem> problems, Throwable cause) {
        if (problems.isEmpty()) {
            return;
        }
        throw new Failure(problems, cause);
    }
}
