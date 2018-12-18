package instrumentation_hello_world;

import io.opencensus.common.Scope;
import io.opencensus.exporter.trace.zipkin.ZipkinTraceExporter;
import io.opencensus.trace.Span;
import io.opencensus.trace.Span.Kind;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import io.opencensus.trace.config.TraceParams;
import io.opencensus.trace.samplers.Samplers;

public class Application {

    private static final Tracer tracer = Tracing.getTracer();

    public static void main(String[] args) throws InterruptedException {
        ZipkinTraceExporter.createAndRegister("http://127.0.0.1:9411/api/v2/spans", "sample-app");

        SampleAzureClient client = new SampleAzureClient("https://azure.microsoft.com");

        String[] azureProducts = new String[] { "analytics", "compute", "containers", "databases", "developer-tools",
                "devops", "identity", "integration", "iot", "management", "microsoft-azure-stack", "networking",
                "security", "storage", "web" };

        TraceParams newParams = TraceParams.DEFAULT.toBuilder().setSampler(Samplers.alwaysSample()).build();
        Tracing.getTraceConfig().updateActiveTraceParams(newParams);

        for (String product : azureProducts) {
            try (Scope s = tracer.spanBuilder("incoming request")
                .setSpanKind(Kind.SERVER).startScopedSpan()) {
                client.get2("/product-categories/" + product);
            }
        }

        Thread.sleep(10000L);
    }
}
