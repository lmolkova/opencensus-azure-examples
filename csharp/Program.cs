using System;
using System.Threading.Tasks;
using OpenCensus.Collector.Dependencies;
using OpenCensus.Exporter.Ocagent;
using OpenCensus.Trace;
using OpenCensus.Trace.Propagation;
using OpenCensus.Trace.Sampler;
using Samples.Instrumentation;

namespace Instrumentation
{
    public class Program
    {
        public static void Main(string [] args)
        {
            ITracer tracer = Tracing.Tracer;
            var exporter = new OcagentExporter(Tracing.ExportComponent,
                "localhost:55678",
                Environment.MachineName,
                "test-app");

            exporter.Start();

            Tracing.TraceConfig.UpdateActiveTraceParams(
                Tracing.TraceConfig.ActiveTraceParams.ToBuilder()
                    .SetSampler(Samplers.AlwaysSample)
                    .Build());

            using (var _ = new DependenciesCollector(new DependenciesCollectorOptions(), Tracing.Tracer,
                Samplers.AlwaysSample, new DefaultPropagationComponent()))
            {
                var sampleClient = new SampleAzureClient("https://azure.microsoft.com");

                var azureProducts = new []{
                    "analytics",
                    "compute",
                    "containers",
                    "databases",
                    "developer-tools",
                    "devops",
                    "identity",
                    "integration",
                    "iot",
                    "management",
                    "microsoft-azure-stack",
                    "networking",
                    "security",
                    "storage",
                    "web"
                };

                Console.WriteLine("Starting...");
                foreach (var product in azureProducts)
                {
                    using (tracer.SpanBuilder("incoming request").StartScopedSpan())
                    {
                        tracer.CurrentSpan.Kind = SpanKind.Server;

                        sampleClient.GetAsync($"/product-categories/{product}").GetAwaiter().GetResult();
                    }
                }

                // TODO: we need to make exporter flush on stop
                Task.Delay(TimeSpan.FromSeconds(5)).GetAwaiter().GetResult();

                exporter.Stop();
            }

            Console.WriteLine("Done! Check out traces on the backend");
        }
    }
}
