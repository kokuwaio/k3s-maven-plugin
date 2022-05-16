package io.kokuwa.maven.k3s.util;

import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class ExecResult {

	private final int exitCode;
	private final List<String> messages;
}
