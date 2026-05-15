package edu.whut.eval.domain.shared;

import java.util.List;

public record PageResult<T>(long total, List<T> records) {
}
