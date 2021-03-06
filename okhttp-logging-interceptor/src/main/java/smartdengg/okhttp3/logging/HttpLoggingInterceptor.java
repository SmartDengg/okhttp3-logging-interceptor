package smartdengg.okhttp3.logging;

import java.io.IOException;
import java.util.List;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 创建时间: 2016/08/09 17:30 <br>
 * 作者: dengwei <br>
 * 描述: 网络连接状态拦截器,主要用于打印网络日志,但不包含响应体
 */
public class HttpLoggingInterceptor implements Interceptor {

  public static boolean DEBUG;
  public static String LOG_TAG;

  private volatile LogLevel level = LogLevel.NONE;
  private RequestDelegate requestDelegate;
  private ResponseDelegate responseDelegate;

  private List<String> exclusiveRequestHeaders;
  private List<String> exclusiveResponseHeaders;
  private List<String> ignoredUrls;

  private Logger logger;

  /**
   * 创建一个默认的网络日志拦截器实例
   */
  public static HttpLoggingInterceptor createLoggingInterceptor() {
    return new HttpLoggingInterceptor(LogLevel.BODY, new Logger.BuiltInLogger(), null, null, null);
  }

  private HttpLoggingInterceptor(LogLevel level, Logger logger, List<String> ignoredUrls,
      List<String> excludeRequestHeaders, List<String> excludeResponseHeaders) {
    this.logger = logger;
    this.level = level;
    this.ignoredUrls = ignoredUrls;
    this.exclusiveRequestHeaders = excludeRequestHeaders;
    this.exclusiveResponseHeaders = excludeResponseHeaders;

    final boolean logBody = level == LogLevel.BODY;
    final boolean logHeaders = logBody || level == LogLevel.HEADERS;

    this.requestDelegate = RequestDelegate.create(logger, logBody, logHeaders);
    this.responseDelegate = ResponseDelegate.create(logger, logBody, logHeaders);
  }

  @Override public Response intercept(Chain chain) throws IOException {

    LogLevel level = this.level;

    Request request = chain.request();
    if (!DEBUG || level == LogLevel.NONE) return chain.proceed(request);

    if (ignoredUrls != null && !ignoredUrls.isEmpty()) {
      for (int i = 0; i < ignoredUrls.size(); i++) {
        if (request.url().toString().contains(ignoredUrls.get(i))) return chain.proceed(request);
      }
    }

    Headers.Builder requestHeadersBuilder = request.headers().newBuilder();
    if (exclusiveRequestHeaders != null && exclusiveRequestHeaders.size() > 0) {
      for (String name : exclusiveRequestHeaders) {
        requestHeadersBuilder.removeAll(name);
      }
    }

    this.requestDelegate.printLog(chain, request, requestHeadersBuilder.build());

    Response response;
    try {
      response = chain.proceed(request);
    } catch (Exception e) {
      logger.log("<-- HTTP FAILED: " + e);
      logger.log(
          "--- we also re-throw this exception, please choose other tag under your monitor to see more information ---");
      logger.logBottomBorder();
      throw e;
    }

    Headers.Builder responseHeadersBuilder = response.headers().newBuilder();
    if (exclusiveResponseHeaders != null && exclusiveResponseHeaders.size() > 0) {
      for (String name : exclusiveResponseHeaders) {
        responseHeadersBuilder.removeAll(name);
      }
    }
    this.responseDelegate.printLog(chain, response, responseHeadersBuilder.build());

    return response;
  }

  public static class Builder {

    private Logger logger;
    private LogLevel logLevel;
    private List<String> excludeRequestHeaders;
    private List<String> excludeResponseHeaders;
    private List<String> skipUrls;

    public Builder() {
    }

    public Builder setLogLevel(LogLevel level) {
      Utils.requireNonNull(level, "level is null");
      this.logLevel = level;
      return Builder.this;
    }

    public Builder setSkipUrls(List<String> urls) {
      Utils.requireNonNull(urls, "urls is null");
      this.skipUrls = urls;
      return Builder.this;
    }

    public Builder setExclusiveHeaders(List<String> requestHeaders, List<String> responseHeaders) {
      Utils.requireNonNull(requestHeaders, "requestHeaders is null");
      Utils.requireNonNull(responseHeaders, "responseHeaders is null");

      this.excludeRequestHeaders = requestHeaders;
      this.excludeResponseHeaders = responseHeaders;
      return Builder.this;
    }

    public Builder setLogger(Logger logger) {
      Utils.requireNonNull(logger, "logger is null");
      this.logger = logger;
      return this;
    }

    public HttpLoggingInterceptor build() {

      if (logger == null) logger = new Logger.BuiltInLogger();

      return new HttpLoggingInterceptor(logLevel, logger, skipUrls, excludeRequestHeaders,
          excludeResponseHeaders);
    }
  }
}
