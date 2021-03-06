package uk.co.autotrader.traverson.http;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import uk.co.autotrader.traverson.conversion.ResourceConversionService;
import uk.co.autotrader.traverson.http.entity.BodyFactory;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ApacheHttpUriConverterTest {
    private ApacheHttpUriConverter apacheHttpUriConverter;
    @Mock
    private BodyFactory bodyFactory;
    @Mock
    private TemplateUriUtils uriUtils;
    @Mock
    private ResourceConversionService conversionService;
    @Mock
    private HttpEntity httpEntity;
    @Mock
    private CloseableHttpResponse httpResponse;

    @Before
    public void setUp() {
        apacheHttpUriConverter = new ApacheHttpUriConverter(bodyFactory, uriUtils, conversionService);
    }

    @Test
    public void toRequest_SetsHttpVerb() {
        Request request = new Request();
        request.setMethod(Method.PATCH);

        ClassicHttpRequest uriRequest = apacheHttpUriConverter.toRequest(request);

        assertThat(uriRequest.getMethod()).isEqualTo("PATCH");
    }

    @Test
    public void toRequest_SetsUrl() throws Exception {
        Request request = new Request();
        request.setMethod(Method.GET);
        String url = "http://localhost:8080/";
        request.setUrl(url);
        when(uriUtils.expandTemplateUri(url, request.getTemplateParams())).thenReturn(url);

        ClassicHttpRequest uriRequest = apacheHttpUriConverter.toRequest(request);

        assertThat(uriRequest.getUri().toASCIIString()).isEqualTo(url);
    }

    @Test
    public void toRequest_AppendsQueryParams() throws Exception {
        Request request = new Request();
        request.setMethod(Method.GET);
        String url = "http://localhost:8080";
        request.setUrl(url);
        request.addQueryParam("key1", "value1");
        request.addQueryParam("key2", "value2");
        when(uriUtils.expandTemplateUri(url, request.getTemplateParams())).thenReturn(url);

        ClassicHttpRequest uriRequest = apacheHttpUriConverter.toRequest(request);

        assertThat(uriRequest.getUri().toASCIIString()).isEqualTo("http://localhost:8080/?key1=value1&key2=value2");
    }

    @Test
    public void toRequest_ExpandsTemplateUriBeforeBuildingRequest() throws Exception {
        Request request = new Request();
        request.setMethod(Method.GET);
        String url = "http://localhost:8080/{tmp1}/stuff{?tmp2}";
        request.setUrl(url);
        request.addTemplateParam("tmp1", "abc");
        request.addTemplateParam("tmp2", "123");
        when(uriUtils.expandTemplateUri(url, request.getTemplateParams())).thenReturn("http://localhost:8080/abc/stuff?tmp2=123");

        ClassicHttpRequest uriRequest = apacheHttpUriConverter.toRequest(request);

        assertThat(uriRequest.getUri().toASCIIString()).isEqualTo("http://localhost:8080/abc/stuff?tmp2=123");
        verify(uriUtils).expandTemplateUri(url, request.getTemplateParams());
    }

    @Test
    public void toRequest_SetsHeaders() {
        Request request = new Request();
        request.setMethod(Method.GET);
        request.addHeader("header1", "value1");
        request.addHeader("header2", "value2");

        ClassicHttpRequest uriRequest = apacheHttpUriConverter.toRequest(request);

        assertThat(uriRequest.getFirstHeader("header1").getValue()).isEqualTo("value1");
        assertThat(uriRequest.getFirstHeader("header2").getValue()).isEqualTo("value2");
    }

    @Test
    public void toRequest_SetsAcceptHeader() {
        Request request = new Request();
        request.setMethod(Method.GET);
        request.setAcceptMimeType("application/json");

        ClassicHttpRequest uriRequest = apacheHttpUriConverter.toRequest(request);

        assertThat(uriRequest.getFirstHeader("Accept").getValue()).isEqualTo("application/json");
    }

    @Test
    public void toRequest_SetsHttpEntity() {
        Body<?> body = mock(Body.class);
        Request request = new Request();
        request.setMethod(Method.PUT);
        request.setBody(body);
        when(bodyFactory.toEntity(body)).thenReturn(httpEntity);

        ClassicHttpRequest uriRequest = apacheHttpUriConverter.toRequest(request);

        assertThat(uriRequest.getEntity()).isEqualTo(httpEntity);
    }

    @Test
    public void toResponse_BuildsResponseCorrectly() throws Exception {
        HttpRequest request =  mock(HttpRequest.class);
        URI requestUri = new URI("http://localhost");

        when(request.getUri()).thenReturn(requestUri);
        when(httpResponse.getCode()).thenReturn(200);
        when(httpResponse.getHeaders()).thenReturn(new Header[]{new BasicHeader("Location", "http://localhost/new")});


        Response<String> response = apacheHttpUriConverter.toResponse(httpResponse, request, String.class);

        assertThat(response).isNotNull();
        assertThat(response.getUri()).isEqualTo(requestUri);
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getResource()).isNull();
        assertThat(response.getResponseHeaders()).containsEntry("Location", "http://localhost/new");
    }

    @Test
    public void toResponse_GivenResponseHasEntity_ConvertsAndSetsResource() throws Exception {
        HttpRequest request =  mock(HttpRequest.class);
        URI requestUri = new URI("http://localhost");
        String expectedJson = "{'name':'test'}";
        InputStream inputStream = Mockito.mock(InputStream.class);

        when(request.getUri()).thenReturn(requestUri);
        when(httpResponse.getEntity()).thenReturn(httpEntity);
        when(httpEntity.getContent()).thenReturn(inputStream);
        when(conversionService.convert(inputStream, String.class)).thenReturn(expectedJson);
        when(httpResponse.getCode()).thenReturn(202);
        when(httpResponse.getHeaders()).thenReturn(new Header[0]);

        Response<String> response = apacheHttpUriConverter.toResponse(httpResponse, request, String.class);

        assertThat(response.getResource()).isEqualTo(expectedJson);
    }

    @Test
    public void toResponse_throwsIllegalArgumentExceptionForAnInvalidURI() throws URISyntaxException {
        HttpRequest request =  mock(HttpRequest.class);
        when(request.getUri()).thenThrow(URISyntaxException.class);

        assertThatThrownBy(() -> apacheHttpUriConverter.toResponse(httpResponse, request, String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The http request contains an invalid URI")
                .hasCauseInstanceOf(URISyntaxException.class);
    }
}
