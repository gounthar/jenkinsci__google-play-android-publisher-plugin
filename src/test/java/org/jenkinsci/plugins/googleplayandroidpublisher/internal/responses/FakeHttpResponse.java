package org.jenkinsci.plugins.googleplayandroidpublisher.internal.responses;

import com.google.api.client.json.GenericJson;
import com.google.api.client.json.Json;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import org.jenkinsci.plugins.googleplayandroidpublisher.internal.TestUtilImpl;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
public class FakeHttpResponse<T extends FakeHttpResponse<? extends T>>
        extends MockLowLevelHttpResponse implements Serializable {
    private static final boolean DEBUG = TestUtilImpl.DEBUG;

    public static FakeHttpResponse NOT_FOUND = forError(404, "not found");

    private String mContent;
    private Map<String, String> mHeaders = new HashMap<>();

    public FakeHttpResponse() {
        super();
        setContentType(Json.MEDIA_TYPE);
        setError(503, "Not implemented");
    }

    public T success() {
        return setSuccessData(null);
    }

    public <D extends GenericJson> T setSuccessData(@Nullable D data) {
        return setResponseData(200, data);
    }

    public <D extends GenericJson> T setResponseData(int statusCode, D data) {
        setStatusCode(statusCode);

        if (data != null) {
            // Need to set a factory in order to get proper JSON serialization
            data.setFactory(JacksonFactory.getDefaultInstance());

            try {
                String str = data.toPrettyString();
                if (DEBUG) System.out.println("Success content: " + str);
                setContent(str);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return (T) this;
    }

    public T setError(int code, String error) {
        setStatusCode(code);
        setContent(String.format("{\"error\":{\"code\":%d,\"message\":\"%s\"}}", code, error));
        return (T) this;
    }

    @Override
    public MockLowLevelHttpResponse setContent(String stringContent) {
        mContent = stringContent;
        return super.setContent(stringContent);
    }

    @Override
    public MockLowLevelHttpResponse addHeader(String name, String value) {
        mHeaders.put(name, value);
        return super.addHeader(name, value);
    }

    public String getSerializedContent() {
        return mContent;
    }

    public Map<String, String> getHeaders() {
        return mHeaders;
    }

    public static FakeHttpResponse forError(int code, String error) {
        return new FakeHttpResponse().setError(code, error);
    }
}
