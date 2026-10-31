package com.kochmarev.statscalculator.quiz;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class QuizAttempt {
    final UUID id = UUID.randomUUID();
    final StatQuiz quiz;
    final UUID ownerId;
    final Instant deadline;
    int answered;
    int correct;
    String lastTask;
    String lastOption;
    String feedback;
    boolean saved;

    QuizAttempt(StatQuiz quiz, UUID ownerId, Instant now) {
        this.quiz = quiz;
        this.ownerId = ownerId;
        this.deadline = quiz.timeLimitSeconds() == 0 ? null : now.plusSeconds(quiz.timeLimitSeconds());
    }

    void requireOwner(UUID currentOwner) {
        if (!Objects.equals(ownerId, currentOwner)) {
            throw new ResponseStatusException(ownerId == null ? HttpStatus.CONFLICT : HttpStatus.UNAUTHORIZED,
                    "Your account changed or session expired. Restore the original sign-in state before continuing.");
        }
    }

    boolean tasksComplete(Instant now) {
        return answered == quiz.tasks().size() || deadline != null && !now.isBefore(deadline);
    }

    void answer(String taskId, String optionId, Instant now, RandomGenerator random) {
        if (saved) throw new ResponseStatusException(HttpStatus.CONFLICT, "This attempt is already saved.");
        if (taskId.equals(lastTask)) {
            if (!optionId.equals(lastOption)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This task already has a different answer.");
            }
            return;
        }
        if (tasksComplete(now)) return;
        var task = quiz.tasks().get(answered);
        if (!task.id().equals(taskId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Answer the current task before advancing.");
        }
        if (task.options().stream().noneMatch(option -> option.id().equals(optionId))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an option from the current task.");
        }
        String actual = task.kind().equals("CHANCE")
                ? task.options().get(random.nextInt(task.options().size())).id() : task.answer();
        boolean success = optionId.equals(actual);
        if (success) correct++;
        if (task.kind().equals("CHANCE")) {
            String label = task.options().stream().filter(option -> option.id().equals(actual))
                    .findFirst().orElseThrow().label();
            feedback = "Random draw: " + label + (success ? " - match." : " - no match.");
        } else {
            feedback = "Selection recorded.";
        }
        answered++;
        lastTask = taskId;
        lastOption = optionId;
    }
}
