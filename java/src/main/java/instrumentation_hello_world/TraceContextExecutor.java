package instrumentation_hello_world;

import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public class TraceContextExecutor implements Executor {

  private final static Tracer tracer = Tracing.getTracer();
  private final static ForkJoinPool asyncPool = ForkJoinPool.commonPool();

  @Override
  public void execute(Runnable command) {
    asyncPool.execute(new WrappedRunnable(command, tracer.getCurrentSpan()));
  }
}
