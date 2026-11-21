package yardmaster.providers;

/**
 * Thrown when a provider returns an error.
 */
public class ProviderException extends RuntimeException {
  private final int statusCode;
  private final String provider;

  /**
   * Constructs a new ProviderException with the given provider and status code.
   *
   * @param provider The name of the provider that returned the error.
   * @param statusCode The status code returned by the provider.
   */
  public ProviderException(String provider, int statusCode) {
    super(String.format("%s returned error code %d", provider, statusCode));
    this.provider = provider;
    this.statusCode = statusCode;
  }

  /**
   * Returns the status code returned by the provider.
   *
   * @return The status code returned by the provider.
   */
  public int statusCode() {
    return statusCode;
  }

  /**
   * Returns the name of the provider that returned the error.
   *
   * @return The name of the provider that returned the error.
   */
  public String provider() {
    return provider;
  }
}
