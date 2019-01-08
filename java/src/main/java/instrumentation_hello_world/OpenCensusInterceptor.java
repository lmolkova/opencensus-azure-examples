package instrumentation_hello_world;

import java.io.IOException;

import io.opencensus.common.Scope;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.Span.Options;
import io.opencensus.trace.propagation.TextFormat;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Request.Builder;

class OpenCensusInterceptor implements Interceptor {

  public static final String HTTP_HOST = "http.host";
  public static final String HTTP_PATH = "http.path";
  public static final String HTTP_METHOD = "http.method";
  public static final String HTTP_URL = "http.url";
  public static final String HTTP_STATUS_CODE = "http.status_code";

  private static final Tracer tracer = Tracing.getTracer();
  private final TextFormat traceContextFormat = Tracing.getPropagationComponent().getTraceContextFormat();
  private final TextFormat.Setter<Builder> contextSetter = new TextFormat.Setter<Builder>() {
    @Override
    public void put(Builder builder, String key, String value) {
      builder.addHeader(key, value);
    }
  };

  @Override
  public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    try (Scope s = tracer.spanBuilder(getSpanName(request)).startScopedSpan()) {
      Span span = tracer.getCurrentSpan();
      if (span.getOptions().contains(Options.RECORD_EVENTS)) {
        addSpanRequestAttributes(span, request);
      }

      Builder builder = request.newBuilder();
      traceContextFormat.inject(span.getContext(), builder, contextSetter);
      Request newRequest = builder.build();

      Response response = null;
      Throwable error = null;
      try {
        response = chain.proceed(newRequest);
        return response;
      } catch (IOException ex) {
        error = ex;
        throw ex;
      } finally {
        spanEnd(span, response, error);
      }
    }
  }

  void spanEnd(Span span, Response response, Throwable error) {
    int statusCode = response == null ? 0 : response.code();
    if (span.getOptions().contains(Options.RECORD_EVENTS)) {
      span.putAttribute(HTTP_STATUS_CODE, AttributeValue.longAttributeValue(statusCode));
    }
    span.setStatus(HttpTraceUtil.parseResponseStatus(statusCode, error));
  }

  final String getSpanName(Request request) {
    // default span name
    String path = request.url().encodedPath();

    if (!path.startsWith("/")) {
      path = "/" + path;
    }
    return path;
  }

  final void addSpanRequestAttributes(Span span, Request request) {
    putAttributeIfNotEmptyOrNull(span, HTTP_HOST, request.url().host());
    putAttributeIfNotEmptyOrNull(span, HTTP_METHOD, request.method());
    putAttributeIfNotEmptyOrNull(span, HTTP_PATH, request.url().encodedPath());
    putAttributeIfNotEmptyOrNull(span, HTTP_URL, request.url().toString());
  }

  private static void putAttributeIfNotEmptyOrNull(Span span, String key, String value) {
    if (value != null && !value.isEmpty()) {
      span.putAttribute(key, AttributeValue.stringAttributeValue(value));
    }
  }
}