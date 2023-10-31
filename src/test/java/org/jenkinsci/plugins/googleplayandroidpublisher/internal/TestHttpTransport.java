package org.jenkinsci.plugins.googleplayandroidpublisher.internal;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.responses.FakeHttpResponse;

public class TestHttpTransport extends MockHttpTransport implements Serializable {
    private static final boolean DEBUG = TestUtilImpl.DEBUG;

    public final Map<String, SimpleResponse> responses = new HashMap<>();
    private List<RemoteCall> remoteCalls = new ArrayList<>();

    @Override
    public LowLevelHttpRequest buildRequest(String method, String url) {
        if (DEBUG) System.out.println("Building request: " + method + " " + url + " on " + this);

        // Iterate through the configured responses, until we find a matching URL
        LowLevelHttpResponse response = null;
        for (Map.Entry<String, SimpleResponse> mockedEntry : responses.entrySet()) {
            if (url.endsWith(mockedEntry.getKey())) {
                response = createResponse(mockedEntry.getValue());
            }
        }

        if (response == null) {
            throw new RuntimeException("Could not find a mocked response for " + method + " to " + url);
        }

        MockLowLevelHttpRequest request = new FakeHttpRequest(response);
        remoteCalls.add(new RemoteCall(method, url, request, response));
        return request;
    }

    private LowLevelHttpResponse createResponse(SimpleResponse response) {
        MockLowLevelHttpResponse httpResponse = new FakeHttpResponse()
                .setStatusCode(response.statusCode)
                .setContent(response.jsonContent);
        response.headers.forEach(httpResponse::addHeader);
        return httpResponse;
    }

    /**
     * Register a {@code response} to handle a request to the {@code url}.
     *
     * @param url A substring that should match the <b>end</b> of the remote URL endpoint
     * @param response The {@link FakeHttpResponse} that will be returned
     * @return {@code this} to enable method call chaining.
     */
    public TestHttpTransport withResponse(String url, FakeHttpResponse response) {
        if (DEBUG) {
            System.out.println("Adding response: " + url + " => " + response);
        }

        responses.put(url, new SimpleResponse(response));
        return this;
    }

    public List<RemoteCall> getRemoteCalls() {
        return remoteCalls;
    }

    public void dumpRequests() {
        if (DEBUG) {
            System.out.println("Attempted requests:");

            for (RemoteCall request : remoteCalls) {
                System.out.println(" - " + request);
            }
        }
    }

    public static class RemoteCall implements Serializable {
        public final String method;
        public final String url;
        public final MockLowLevelHttpRequest request;
        private final LowLevelHttpResponse response;

        RemoteCall(String method, String url, MockLowLevelHttpRequest request, LowLevelHttpResponse response) {
            this.method = method;
            this.url = url;
            this.request = request;
            this.response = response;
        }

        @Nullable
        public LowLevelHttpResponse getResponse() {
            return response;
        }

        @Override
        public String toString() {
            return method + " " + url;
        }
    }

    static class FakeHttpRequest extends MockLowLevelHttpRequest implements Serializable {
        final LowLevelHttpResponse response;

        FakeHttpRequest(LowLevelHttpResponse response) {
            this.response = response;
        }

        @Override
        public LowLevelHttpResponse execute() {
            return this.response;
        }
    }

    public static class SimpleResponse implements Serializable {
        public final int statusCode;
        public final String jsonContent;
        public final Map<String, String> headers;

        private SimpleResponse(int statusCode, String jsonContent, Map<String, String> headers) {
            this.statusCode = statusCode;
            this.jsonContent = jsonContent;
            this.headers = headers;
        }

        public SimpleResponse(FakeHttpResponse response) {
            this(response.getStatusCode(), response.getSerializedContent(), response.getHeaders());
        }
    }
}
