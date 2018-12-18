package instrumentation_hello_world;

import io.opencensus.common.Scope;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;

public class WrappedRunnable implements Runnable{

  private static final Tracer tracer = Tracing.getTracer();
  private final Span span;
  private final Runnable internalRunnable;
  public WrappedRunnable(Runnable runnable, Span span) {
    this.span = span;
    this.internalRunnable = runnable;
  }

  @Override
  public void run() {
    try (Scope s = tracer.withSpan(span)) {
      this.internalRunnable.run();
    }
  }
}
