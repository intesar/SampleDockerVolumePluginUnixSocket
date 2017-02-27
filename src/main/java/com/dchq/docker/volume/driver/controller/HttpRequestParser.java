package com.dchq.docker.volume.driver.controller;

import org.apache.commons.io.IOUtils;
import org.apache.http.Consts;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ContentLengthStrategy;
import org.apache.http.impl.entity.StrictContentLengthStrategy;
import org.apache.http.impl.io.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;

public class HttpRequestParser {


    public static RequestWrapper parse(String request_str) {
        try {
            SessionInputBufferImpl sessionInputBuffer = new SessionInputBufferImpl(new HttpTransportMetricsImpl(), 2048);
            sessionInputBuffer.bind(new ByteArrayInputStream(request_str.getBytes(Consts.ASCII)));
            DefaultHttpRequestParser requestParser = new DefaultHttpRequestParser(sessionInputBuffer);
            org.apache.http.HttpRequest request = requestParser.parse();

            // Solve post request entity bug (always returns null)
            if (request instanceof HttpEntityEnclosingRequest) {
                HttpEntityEnclosingRequest ereq = (HttpEntityEnclosingRequest) request;

                ContentLengthStrategy contentLengthStrategy = StrictContentLengthStrategy.INSTANCE;
                long len = contentLengthStrategy.determineLength(request);
                InputStream contentStream = null;
                if (len == ContentLengthStrategy.CHUNKED) {
                    contentStream = new ChunkedInputStream(sessionInputBuffer);
                } else if (len == ContentLengthStrategy.IDENTITY) {
                    contentStream = new IdentityInputStream(sessionInputBuffer);
                } else {
                    contentStream = new ContentLengthInputStream(sessionInputBuffer, len);
                }
                BasicHttpEntity ent = new BasicHttpEntity();

                StringWriter writer = new StringWriter();
                IOUtils.copy(contentStream, writer, "UTF-8");
                String body = writer.toString();

                RequestWrapper wrapper = new RequestWrapper(request.getRequestLine().getUri(), body);

                return wrapper;
                //return body;
                //ent.setContent(contentStream);
                //ereq.setEntity(ent);
                //return ereq;
            }

            return null;
        } catch (Exception e) {
//	        logger.error("Unable to parse request", e);
            e.printStackTrace();
            return null;
        }
    }
}

class RequestWrapper {
    private String path;
    private String body;

    public RequestWrapper(String path, String body) {
        this.path = path;
        this.body = body;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}