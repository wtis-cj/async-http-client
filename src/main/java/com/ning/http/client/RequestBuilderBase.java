/*
 * Copyright 2010 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.ning.http.client;

import static com.ning.http.util.MiscUtil.isNonEmpty;

import java.io.File;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.http.client.Request.EntityWriter;
import com.ning.http.client.cookie.Cookie;
import com.ning.http.client.uri.UriComponents;
import com.ning.http.util.AsyncHttpProviderUtils;
import com.ning.http.util.UTF8UrlEncoder;

/**
 * Builder for {@link Request}
 * 
 * @param <T>
 */
public abstract class RequestBuilderBase<T extends RequestBuilderBase<T>> {
    private final static Logger logger = LoggerFactory.getLogger(RequestBuilderBase.class);

    private static final UriComponents DEFAULT_REQUEST_URL = UriComponents.create("http://localhost");

    private static final class RequestImpl implements Request {
        private String method;
        private UriComponents originalUri;
        private UriComponents uri;
        private UriComponents rawUri;
        private InetAddress address;
        private InetAddress localAddress;
        private FluentCaseInsensitiveStringsMap headers = new FluentCaseInsensitiveStringsMap();
        private ArrayList<Cookie> cookies;
        private byte[] byteData;
        private String stringData;
        private InputStream streamData;
        private EntityWriter entityWriter;
        private BodyGenerator bodyGenerator;
        private FluentStringsMap formParams;
        private List<Part> parts;
        private String virtualHost;
        private long length = -1;
        public FluentStringsMap queryParams;
        public ProxyServer proxyServer;
        private Realm realm;
        private File file;
        private Boolean followRedirects;
        private int requestTimeoutInMs;
        private long rangeOffset;
        public String charset;
        private boolean useRawUrl;
        private ConnectionPoolKeyStrategy connectionPoolKeyStrategy = DefaultConnectionPoolStrategy.INSTANCE;

        public RequestImpl(boolean useRawUrl) {
            this.useRawUrl = useRawUrl;
        }

        public RequestImpl(Request prototype) {
            if (prototype != null) {
                this.method = prototype.getMethod();
                this.originalUri = prototype.getOriginalURI();
                this.address = prototype.getInetAddress();
                this.localAddress = prototype.getLocalAddress();
                this.headers = new FluentCaseInsensitiveStringsMap(prototype.getHeaders());
                this.cookies = new ArrayList<Cookie>(prototype.getCookies());
                this.byteData = prototype.getByteData();
                this.stringData = prototype.getStringData();
                this.streamData = prototype.getStreamData();
                this.entityWriter = prototype.getEntityWriter();
                this.bodyGenerator = prototype.getBodyGenerator();
                this.formParams = prototype.getFormParams() == null ? null : new FluentStringsMap(prototype.getFormParams());
                this.queryParams = prototype.getQueryParams() == null ? null : new FluentStringsMap(prototype.getQueryParams());
                this.parts = prototype.getParts() == null ? null : new ArrayList<Part>(prototype.getParts());
                this.virtualHost = prototype.getVirtualHost();
                this.length = prototype.getContentLength();
                this.proxyServer = prototype.getProxyServer();
                this.realm = prototype.getRealm();
                this.file = prototype.getFile();
                this.followRedirects = prototype.isRedirectOverrideSet() ? prototype.isRedirectEnabled() : null;
                this.requestTimeoutInMs = prototype.getRequestTimeoutInMs();
                this.rangeOffset = prototype.getRangeOffset();
                this.charset = prototype.getBodyEncoding();
                this.useRawUrl = prototype.isUseRawUrl();
                this.connectionPoolKeyStrategy = prototype.getConnectionPoolKeyStrategy();
            }
        }

        /* @Override */
        public String getMethod() {
            return method;
        }

        public InetAddress getInetAddress() {
            return address;
        }

        public InetAddress getLocalAddress() {
            return localAddress;
        }

        private String removeTrailingSlash(UriComponents uri) {
            String uriString = uri.toString();
            if (uriString.endsWith("/")) {
                return uriString.substring(0, uriString.length() - 1);
            } else {
                return uriString;
            }
        }

        /* @Override */
        public String getUrl() {
            return removeTrailingSlash(getURI());
        }

        /* @Override */
        public String getRawUrl() {
            return removeTrailingSlash(getRawURI());
        }

        public UriComponents getOriginalURI() {
            return originalUri;
        }

        public UriComponents getURI() {
            if (uri == null)
                uri = toUriComponents(true);
            return uri;
        }

        public UriComponents getRawURI() {
            if (rawUri == null)
                rawUri = toUriComponents(false);
            return rawUri;
        }

        private UriComponents toUriComponents(boolean encode) {

            if (originalUri == null) {
                logger.debug("setUrl hasn't been invoked. Using http://localhost");
                originalUri = DEFAULT_REQUEST_URL;
            }

            AsyncHttpProviderUtils.validateSupportedScheme(originalUri);

            String newPath = isNonEmpty(originalUri.getPath())? originalUri.getPath() : "/";
            String newQuery = null;
            if (isNonEmpty(queryParams)) {
                StringBuilder sb = new StringBuilder();
                for (Iterator<Entry<String, List<String>>> i = queryParams.iterator(); i.hasNext();) {
                    Map.Entry<String, List<String>> param = i.next();
                    String name = param.getKey();
                    for (Iterator<String> j = param.getValue().iterator(); j.hasNext();) {
                        String value = j.next();
                        if (encode) {
                            UTF8UrlEncoder.appendEncoded(sb, name);
                        } else {
                            sb.append(name);
                        }
                        if (value != null) {
                            sb.append('=');
                            if (encode) {
                                UTF8UrlEncoder.appendEncoded(sb, value);
                            } else {
                                sb.append(value);
                            }
                        }
                        if (j.hasNext()) {
                            sb.append('&');
                        }
                    }
                    if (i.hasNext()) {
                        sb.append('&');
                    }
                }

                newQuery = sb.toString();
            }

            return new UriComponents(//
                    originalUri.getScheme(),//
                    originalUri.getUserInfo(),//
                    originalUri.getHost(),//
                    originalUri.getPort(),//
                    newPath,//
                    newQuery);
        }

        /* @Override */
        public FluentCaseInsensitiveStringsMap getHeaders() {
            return headers;
        }

        /* @Override */
        public Collection<Cookie> getCookies() {
            return cookies != null ? Collections.unmodifiableCollection(cookies) : Collections.<Cookie> emptyList();
        }

        /* @Override */
        public byte[] getByteData() {
            return byteData;
        }

        /* @Override */
        public String getStringData() {
            return stringData;
        }

        /* @Override */
        public InputStream getStreamData() {
            return streamData;
        }

        /* @Override */
        public EntityWriter getEntityWriter() {
            return entityWriter;
        }

        /* @Override */
        public BodyGenerator getBodyGenerator() {
            return bodyGenerator;
        }

        /* @Override */
        public long getContentLength() {
            return length;
        }

        /* @Override */
        public FluentStringsMap getFormParams() {
            return formParams;
        }

        /* @Override */
        public List<Part> getParts() {
            return parts;
        }

        /* @Override */
        public String getVirtualHost() {
            return virtualHost;
        }

        public FluentStringsMap getQueryParams() {
            return queryParams;
        }

        public ProxyServer getProxyServer() {
            return proxyServer;
        }

        public Realm getRealm() {
            return realm;
        }

        public File getFile() {
            return file;
        }

        public boolean isRedirectEnabled() {
            return followRedirects != null && followRedirects;
        }

        public boolean isRedirectOverrideSet() {
            return followRedirects != null;
        }

        public int getRequestTimeoutInMs() {
            return requestTimeoutInMs;
        }

        public long getRangeOffset() {
            return rangeOffset;
        }

        public String getBodyEncoding() {
            return charset;
        }

        public ConnectionPoolKeyStrategy getConnectionPoolKeyStrategy() {
            return connectionPoolKeyStrategy;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(getURI().toString());

            sb.append("\t");
            sb.append(method);
            sb.append("\theaders:");
            if (isNonEmpty(headers)) {
                for (String name : headers.keySet()) {
                    sb.append("\t");
                    sb.append(name);
                    sb.append(":");
                    sb.append(headers.getJoinedValue(name, ", "));
                }
            }
            if (isNonEmpty(formParams)) {
                sb.append("\tformParams:");
                for (String name : formParams.keySet()) {
                    sb.append("\t");
                    sb.append(name);
                    sb.append(":");
                    sb.append(formParams.getJoinedValue(name, ", "));
                }
            }

            return sb.toString();
        }

        public boolean isUseRawUrl() {
            return useRawUrl;
        }
    }

    private final Class<T> derived;
    protected final RequestImpl request;
    protected boolean useRawUrl = false;
    protected String baseURL;
    protected SignatureCalculator signatureCalculator;

    protected RequestBuilderBase(Class<T> derived, String method, boolean rawUrls) {
        this.derived = derived;
        request = new RequestImpl(rawUrls);
        request.method = method;
        this.useRawUrl = rawUrls;
    }

    protected RequestBuilderBase(Class<T> derived, Request prototype) {
        this.derived = derived;
        request = new RequestImpl(prototype);
        this.useRawUrl = prototype.isUseRawUrl();
    }

    public T setUrl(String url) {
        this.baseURL = url;
        return setURI(UriComponents.create(url));
    }

    public T setURI(UriComponents uri) {
        if (uri.getPath() == null)
            throw new NullPointerException("uri.path");
        request.originalUri = uri;
        addQueryParams(request.originalUri);
        request.uri = null;
        request.rawUri = null;
        return derived.cast(this);
    }

    public T setInetAddress(InetAddress address) {
        request.address = address;
        return derived.cast(this);
    }

    public T setLocalInetAddress(InetAddress address) {
        request.localAddress = address;
        return derived.cast(this);
    }

    private void addQueryParams(UriComponents uri) {
        if (isNonEmpty(uri.getQuery())) {
            String[] queries = uri.getQuery().split("&");
            int pos;
            for (String query : queries) {
                pos = query.indexOf("=");
                if (pos <= 0) {
                    addQueryParam(query, null);
                } else {
                    try {
                        if (useRawUrl) {
                            addQueryParam(query.substring(0, pos), query.substring(pos + 1));
                        } else {
                            addQueryParam(URLDecoder.decode(query.substring(0, pos), "UTF-8"), URLDecoder.decode(query.substring(pos + 1), "UTF-8"));
                        }
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    public T setVirtualHost(String virtualHost) {
        request.virtualHost = virtualHost;
        return derived.cast(this);
    }

    public T setHeader(String name, String value) {
        request.headers.replace(name, value);
        return derived.cast(this);
    }

    public T addHeader(String name, String value) {
        if (value == null) {
            logger.warn("Value was null, set to \"\"");
            value = "";
        }

        request.headers.add(name, value);
        return derived.cast(this);
    }

    public T setHeaders(FluentCaseInsensitiveStringsMap headers) {
        request.headers = (headers == null ? new FluentCaseInsensitiveStringsMap() : new FluentCaseInsensitiveStringsMap(headers));
        return derived.cast(this);
    }

    public T setHeaders(Map<String, Collection<String>> headers) {
        request.headers = (headers == null ? new FluentCaseInsensitiveStringsMap() : new FluentCaseInsensitiveStringsMap(headers));
        return derived.cast(this);
    }

    public T setContentLength(int length) {
        request.length = length;
        return derived.cast(this);
    }

    private void lazyInitCookies() {
        if (request.cookies == null)
            request.cookies = new ArrayList<Cookie>(3);
    }

    public T setCookies(Collection<Cookie> cookies) {
        request.cookies = new ArrayList<Cookie>(cookies);
        return derived.cast(this);
    }

    public T addCookie(Cookie cookie) {
        lazyInitCookies();
        request.cookies.add(cookie);
        return derived.cast(this);
    }

    public T addOrReplaceCookie(Cookie cookie) {
        String cookieKey = cookie.getName();
        boolean replace = false;
        int index = 0;
        lazyInitCookies();
        for (Cookie c : request.cookies) {
            if (c.getName().equals(cookieKey)) {
                replace = true;
                break;
            }

            index++;
        }
        if (replace)
            request.cookies.set(index, cookie);
        else
            request.cookies.add(cookie);
        return derived.cast(this);
    }
    
    public void resetCookies() {
        if (request.cookies != null)
            request.cookies.clear();
    }
    
    public void resetQueryParams() {
        request.queryParams = null;
    }
    
    public void resetFormParams() {
        request.formParams = null;
    }

    public void resetNonMultipartData() {
        request.byteData = null;
        request.stringData = null;
        request.streamData = null;
        request.entityWriter = null;
        request.length = -1;
    }

    public void resetMultipartData() {
        request.parts = null;
    }

    public T setBody(File file) {
        request.file = file;
        return derived.cast(this);
    }

    public T setBody(byte[] data) {
        resetFormParams();
        resetNonMultipartData();
        resetMultipartData();
        request.byteData = data;
        return derived.cast(this);
    }

    public T setBody(String data) {
        resetFormParams();
        resetNonMultipartData();
        resetMultipartData();
        request.stringData = data;
        return derived.cast(this);
    }

    public T setBody(InputStream stream) {
        resetFormParams();
        resetNonMultipartData();
        resetMultipartData();
        request.streamData = stream;
        return derived.cast(this);
    }

    public T setBody(EntityWriter dataWriter) {
        return setBody(dataWriter, -1);
    }

    public T setBody(EntityWriter dataWriter, long length) {
        resetFormParams();
        resetNonMultipartData();
        resetMultipartData();
        request.entityWriter = dataWriter;
        request.length = length;
        return derived.cast(this);
    }

    public T setBody(BodyGenerator bodyGenerator) {
        request.bodyGenerator = bodyGenerator;
        return derived.cast(this);
    }

    public T addQueryParam(String name, String value) {
        if (request.queryParams == null) {
            request.queryParams = new FluentStringsMap();
        }
        request.queryParams.add(name, value);
        return derived.cast(this);
    }

    public T setQueryParam(FluentStringsMap parameters) {
        if (parameters == null) {
            request.queryParams = null;
        } else {
            request.queryParams = new FluentStringsMap(parameters);
        }
        return derived.cast(this);
    }

    public T addFormParam(String key, String value) {
        resetNonMultipartData();
        resetMultipartData();
        if (request.formParams == null)
            request.formParams = new FluentStringsMap();
        request.formParams.add(key, value);
        return derived.cast(this);
    }

    public T setFormParams(FluentStringsMap parameters) {
        resetNonMultipartData();
        resetMultipartData();
        request.formParams = new FluentStringsMap(parameters);
        return derived.cast(this);
    }

    public T setFormParams(Map<String, Collection<String>> parameters) {
        resetNonMultipartData();
        resetMultipartData();
        request.formParams = new FluentStringsMap(parameters);
        return derived.cast(this);
    }

    public T addBodyPart(Part part) {
        resetFormParams();
        resetNonMultipartData();
        if (request.parts == null)
            request.parts = new ArrayList<Part>();
        request.parts.add(part);
        return derived.cast(this);
    }

    public T setProxyServer(ProxyServer proxyServer) {
        request.proxyServer = proxyServer;
        return derived.cast(this);
    }

    public T setRealm(Realm realm) {
        request.realm = realm;
        return derived.cast(this);
    }

    public T setFollowRedirects(boolean followRedirects) {
        request.followRedirects = followRedirects;
        return derived.cast(this);
    }

    public T setRequestTimeoutInMs(int requestTimeoutInMs) {
        request.requestTimeoutInMs = requestTimeoutInMs;
        return derived.cast(this);
    }

    public T setRangeOffset(long rangeOffset) {
        request.rangeOffset = rangeOffset;
        return derived.cast(this);
    }

    public T setMethod(String method) {
        request.method = method;
        return derived.cast(this);
    }

    public T setBodyEncoding(String charset) {
        request.charset = charset;
        return derived.cast(this);
    }

    public T setConnectionPoolKeyStrategy(ConnectionPoolKeyStrategy connectionPoolKeyStrategy) {
        request.connectionPoolKeyStrategy = connectionPoolKeyStrategy;
        return derived.cast(this);
    }

    public T setSignatureCalculator(SignatureCalculator signatureCalculator) {
        this.signatureCalculator = signatureCalculator;
        return derived.cast(this);
    }

    private void executeSignatureCalculator() {
        /* Let's first calculate and inject signature, before finalizing actual build
         * (order does not matter with current implementation but may in future)
         */
        if (signatureCalculator != null) {
            String url = baseURL != null ? baseURL : request.originalUri.toString();
            // Should not include query parameters, ensure:
            int i = url.indexOf('?');
            if (i != -1) {
                url = url.substring(0, i);
            }
            signatureCalculator.calculateAndAddSignature(url, request, this);
        }
    }
    
    private void computeRequestCharset() {
        if (request.charset == null) {
            try {
                final String contentType = request.headers.getFirstValue("Content-Type");
                if (contentType != null) {
                    final String charset = AsyncHttpProviderUtils.parseCharset(contentType);
                    if (charset != null) {
                        // ensure that if charset is provided with the Content-Type header,
                        // we propagate that down to the charset of the Request object
                        request.charset = charset;
                    }
                }
            } catch (Throwable e) {
                // NoOp -- we can't fix the Content-Type or charset from here
            }
        }
    }
    
    private void computeRequestLength() {
        if (request.length < 0 && request.streamData == null) {
            // can't concatenate content-length
            final String contentLength = request.headers.getFirstValue("Content-Length");

            if (contentLength != null) {
                try {
                    request.length = Long.parseLong(contentLength);
                } catch (NumberFormatException e) {
                    // NoOp -- we wdn't specify length so it will be chunked?
                }
            }
        }
    }
    
    public Request build() {
        executeSignatureCalculator();
        computeRequestCharset();
        computeRequestLength();
        return request;
    }
}