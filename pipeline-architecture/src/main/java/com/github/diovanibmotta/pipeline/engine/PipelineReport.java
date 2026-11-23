package com.github.diovanibmotta.pipeline.engine;

import java.util.List;

public record PipelineReport(long produced, long consumed, List<DeadLetter> deadLetters) {
}
