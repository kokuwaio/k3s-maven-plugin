package io.kokuwa.maven.k3s;

/**
 * Possible cache types for agent directory.
 *
 * @since 0.9.0
 */
public enum AgentCacheMode {

	/**
	 * No mount, everything is lost after deletetion.
	 *
	 * @since 0.9.0
	 */
	NONE,

	/**
	 * Mount agent directory from host, images will be reused in newer runs.
	 *
	 * @since 0.9.0
	 */
	HOST
}
