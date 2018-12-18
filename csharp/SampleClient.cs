using System;
using System.Net;
using System.Net.Http;
using System.Threading.Tasks;
using OpenCensus.Trace;

namespace Samples.Instrumentation
{
    // Sample client implementation
    public class SampleAzureClient
    {
        private readonly IAttributeValue<string> endpoint;
        private readonly ITracer tracer;
        private readonly HttpClient httpClient = new HttpClient();
            
        public SampleAzureClient(string endpoint)
        {
            // Cache endpoint attribute.
            // Assuming client calls backend service, this is
            // service endpoint that includes user's account or tenant id
            // Storage request endpoint (URI) is a good  candidate. 
            this.endpoint = AttributeValue.StringAttributeValue(endpoint);

            this.tracer = Tracing.Tracer;

            // initialization
            httpClient.BaseAddress = new Uri(endpoint);
            // add retry handler...
        }

        /// <summary>
        /// Sample operation that gets some data from the remote endpoint.
        /// </summary>
        /// <param name="path">Path to resource to get.</param>
        /// <returns>Some response with requested data.</returns>
        public async Task<string> GetAsync(string path)
        {
            string result = null;

            // First, start the scoped span
            // Span name follows 'component/operation name' pattern.
            using (this.tracer.SpanBuilder("azure.sample/get")
                .StartScopedSpan())
            {
                // Span is stored in AsyncLocal and we can always get current one:
                var currentSpan = this.tracer.CurrentSpan;

                // check if span is sampled, if not - we don't need to add properties.
                bool isSampled = (currentSpan.Options & SpanOptions.RecordEvents) != 0;

                // Let's augment sampled spans only
                if (isSampled)
                {
                    currentSpan.Kind = SpanKind.Client;
                    currentSpan.PutAttribute("az.endpoint", endpoint);
                    currentSpan.PutAttribute("path", AttributeValue.StringAttributeValue(path));
                }

                try
                {
                    // Get the data
                    using (var response = await DoInternalWithRetriesAsync(path).ConfigureAwait(false))
                    {
                        result = await response.Content.ReadAsStringAsync().ConfigureAwait(false);
                        if (isSampled)
                        {
                            // No exception happened. In some cases it means success
                            // but we may also get bad response
                            currentSpan.Status = ToStatus(response.StatusCode);
                        }
                    }
                }
                catch (Exception ex)
                {
                    if (isSampled)
                    {
                        // failed, let's fill the status
                        currentSpan.Status = ToStatus(ex);
                    }

                    throw;
                }
            }

            
            return result;
        }

        /// <summary>
        /// Gets data from the backend service, executes retries and possibly runs
        /// multiple calls to the backend.
        /// </summary>
        /// <param name="path">Path to resource.</param>
        /// <returns>Some response.</returns>
        public Task<HttpResponseMessage> DoInternalWithRetriesAsync(string path)
        {
            return this.httpClient.GetAsync(path);
        }

        /// <summary>
        /// Converts library status code into OpenCensus response code.
        /// </summary>
        /// <param name="responseCode">Library's status code value.</param>
        /// <returns>OpenCensus status code.</returns>
        private static Status ToStatus(HttpStatusCode responseCode)
        {
            var intCode = (int) responseCode;
            // HTTP status codes are used just an example
            if (intCode >= 200 && intCode < 400)
                return Status.Ok.WithDescription(responseCode.ToString());

            switch (intCode)
            {
                case 400:
                    return Status.InvalidArgument;
                case 401:
                    return Status.Unauthenticated;
                case 403:
                    return Status.PermissionDenied;
                case 404:
                    return Status.NotFound;
                case 409:
                    return Status.AlreadyExists;
                case 429:
                    return Status.ResourceExhausted;
                case 499:
                    return Status.Cancelled;
                case 501:
                    return Status.Unimplemented;
                case 503:
                    return Status.Unavailable;
                case 504:
                    return Status.DeadlineExceeded;
                default:
                    return Status.Unknown.WithDescription(responseCode.ToString());
            }
        }

        /// <summary>
        /// Converts exception to OpenCensus status based on it's type.
        /// </summary>
        /// <param name="e">Exception instance.</param>
        /// <returns>OpenCensus status.</returns>
        public static Status ToStatus(Exception e)
        {
            switch (e)
            {
                case TimeoutException toe:
                    return Status.DeadlineExceeded.WithDescription(toe.ToString());
                case TaskCanceledException tce:
                    return Status.Cancelled.WithDescription(tce.ToString());
                default:
                    return Status.Unknown.WithDescription(e.ToString());
            }
        }
    }
}
