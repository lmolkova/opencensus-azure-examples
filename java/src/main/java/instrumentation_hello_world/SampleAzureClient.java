package instrumentation_hello_world;

import io.opencensus.common.Scope;
import io.opencensus.trace.AttributeValue;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.Span.Options;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class SampleAzureClient {

    private final AttributeValue endpointAttribute;
    private final OkHttpClient httpClient;
    private final String endpoint;
    private static final Tracer tracer = Tracing.getTracer();
    private static final TraceContextExecutor traceContextExecutor = new TraceContextExecutor();

    public SampleAzureClient(String endpoint) {

        this.endpoint = endpoint;

        // configure http client/AutoRest.
        // make sure it includes network interceptor for tracing
        // (which should happen by default or with some configuration).
        this.httpClient = new OkHttpClient.Builder().addNetworkInterceptor(new OpenCensusInterceptor()).build();

        // cache the endpoint attribute - we'll add it to every span
        // Assuming client calls backend service, this is
        // service endpoint that includes user's account or tenant id
        // Storage request endpoint (URI) is a good  candidate.
        this.endpointAttribute = AttributeValue.stringAttributeValue(endpoint);
    }

    // Example of synchronous operation that gets data from the path.
    public String get1(String path) {
        // Starting a scoped span. Nested spans created under this scope will be children of this span
        // Set kind to the 'CLIENT' and use defined component ('Azure.Sample') and operation ('get') name
        try (Scope c = tracer.spanBuilder("azure.sample/get").setSpanKind(Span.Kind.CLIENT).startScopedSpan()) {

            Span currentSpan = tracer.getCurrentSpan();

            // check if span is sampled. If not - it will not be recorded by the tracing system
            // i.e. adding extra properties is a pure overhead.
            // If it is sampled - add endpoint and library-specific context (path)
            boolean isSampled = currentSpan.getOptions().contains(Options.RECORD_EVENTS);
            if (isSampled) {
                currentSpan.putAttribute("az.endpoint", endpointAttribute);
                currentSpan.putAttribute("path", AttributeValue.stringAttributeValue(path));
            }

            Response response = null;
            Throwable error = null;
            String result = null;
            try {
                response = doInternal(path);
                if (response != null) {
                    try (ResponseBody body = response.body()) {
                        result = body.string();
                    }
                }
            } catch (IOException e) {
                error = e;
                // process exception
            } finally {

                // set status of the span - you can use opencensus helpers, or write your own using
                // this helper as reference
                currentSpan.setStatus(HttpTraceUtil.parseResponseStatus(response != null ? response.code() : 0, error));
            }
            return result;
        }
    }

    // this is an example of asynchronous operation that gets data from the service -
    // it just wraps synchronous get1
    // it demonstrates how to propagate current span (and trace context) in-process through async calls
    public String get2(String path) {
        Span currentSpan = tracer.getCurrentSpan();
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {

            // Current span is thread local, we've just been assigned with
            // the thread pool thread and lost current span.
            // If we don't restore it, we've lost correlation - nested spans will have
            // brand new trace context, so let's restore it.

            // It has to be done in each explicit or implicit async operation
            // that uses any approach to async programming (threads, callbacks, futures, etc...)
            // Simple approach could be to create custom Executor that will capture current span
            // on start and restore it on 'execute'.
            try (Scope s = tracer.withSpan(currentSpan)) {
                return this.get1(path);
            }
        });

        try {
            return future.get();
        } catch (InterruptedException ie) {
            ie.printStackTrace();
            // process exception
        } catch (ExecutionException ee) {
            ee.printStackTrace();
            // process exception
        }
        return null;
    }

  // this is an example of asynchronous operation that gets data from the service -
  // it just wraps synchronous get1
  // it demonstrates how to propagate current span (and trace context) in-process through async calls
  // via custom Executor
  public String get3(String path) {
    CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
      return this.get1(path);
    },
        // this time we don't explicitly propagate current span
        // but we use custom executor that does it for us
        traceContextExecutor);

    try {
      return future.get();
    } catch (InterruptedException ie) {
      ie.printStackTrace();
      // process exception
    } catch (ExecutionException ee) {
      ee.printStackTrace();
      // process exception
    }
    return null;
  }

    // execute request with retries, etc...
    private Response doInternal(String path) throws IOException {
        HttpUrl url = HttpUrl.parse(endpoint + path);
        Request request = new Request.Builder().url(url).build();
        return this.httpClient.newCall(request).execute();
    }
}
