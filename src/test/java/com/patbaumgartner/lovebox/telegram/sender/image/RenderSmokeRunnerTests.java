package com.patbaumgartner.lovebox.telegram.sender.image;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class RenderSmokeRunnerTests {

	@Test
	void doesNothingWhenDisabled() throws Exception {
		List<Integer> shutdowns = new ArrayList<>();
		RenderSmokeRunner runner = runner(new MockEnvironment(), shutdowns);

		runner.run(null);

		assertThat(shutdowns).isEmpty();
	}

	@Test
	void rendersAllPipelinesAndShutsDownWhenEnabled() {
		List<Integer> shutdowns = new ArrayList<>();
		MockEnvironment environment = new MockEnvironment().withProperty(RenderSmokeRunner.ENABLED_PROPERTY, "true");
		RenderSmokeRunner runner = runner(environment, shutdowns);

		assertThatNoException().isThrownBy(() -> runner.run(null));

		assertThat(shutdowns).containsExactly(0);
	}

	private RenderSmokeRunner runner(MockEnvironment environment, List<Integer> shutdowns) {
		return new RenderSmokeRunner(environment, new ImageService(), null) {
			@Override
			protected void shutdown() {
				shutdowns.add(0);
			}
		};
	}

}
