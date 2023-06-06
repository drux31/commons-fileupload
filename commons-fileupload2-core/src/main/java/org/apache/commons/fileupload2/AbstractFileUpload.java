/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.fileupload2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.fileupload2.util.FileItemHeadersImpl;
import org.apache.commons.io.IOUtils;

/**
 * High level API for processing file uploads.
 * <p>
 * This class handles multiple files per single HTML widget, sent using {@code multipart/mixed} encoding type, as specified by
 * <a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a>. Use {@link #parseRequest(RequestContext)} to acquire a list of
 * {@link org.apache.commons.fileupload2.FileItem}s associated with a given HTML widget.
 * </p>
 * <p>
 * How the data for individual parts is stored is determined by the factory used to create them; a given part may be in memory, on disk, or somewhere else.
 * </p>
 */
public abstract class AbstractFileUpload {

    /**
     * HTTP content type header name.
     */
    public static final String CONTENT_TYPE = "Content-type";

    /**
     * HTTP content disposition header name.
     */
    public static final String CONTENT_DISPOSITION = "Content-disposition";

    /**
     * HTTP content length header name.
     */
    public static final String CONTENT_LENGTH = "Content-length";

    /**
     * Content-disposition value for form data.
     */
    public static final String FORM_DATA = "form-data";

    /**
     * Content-disposition value for file attachment.
     */
    public static final String ATTACHMENT = "attachment";

    /**
     * Part of HTTP content type header.
     */
    public static final String MULTIPART = "multipart/";

    /**
     * HTTP content type header for multipart forms.
     */
    public static final String MULTIPART_FORM_DATA = "multipart/form-data";

    /**
     * HTTP content type header for multiple uploads.
     */
    public static final String MULTIPART_MIXED = "multipart/mixed";

    /**
     * Utility method that determines whether the request contains multipart content.
     * <p>
     * <strong>NOTE:</strong> This method will be moved to the {@code ServletFileUpload} class after the FileUpload 1.1 release. Unfortunately, since this
     * method is static, it is not possible to provide its replacement until this method is removed.
     * </p>
     *
     * @param ctx The request context to be evaluated. Must be non-null.
     * @return {@code true} if the request is multipart; {@code false} otherwise.
     */
    public static final boolean isMultipartContent(final RequestContext ctx) {
        final String contentType = ctx.getContentType();
        if (contentType == null) {
            return false;
        }
        return contentType.toLowerCase(Locale.ENGLISH).startsWith(MULTIPART);
    }

    /**
     * The maximum size permitted for the complete request, as opposed to {@link #fileSizeMax}. A value of -1 indicates no maximum.
     */
    private long sizeMax = -1;

    /**
     * The maximum size permitted for a single uploaded file, as opposed to {@link #sizeMax}. A value of -1 indicates no maximum.
     */
    private long fileSizeMax = -1;

    /**
     * The maximum permitted number of files that may be uploaded in a single request. A value of -1 indicates no maximum.
     */
    private long fileCountMax = -1;

    /**
     * The content encoding to use when reading part headers.
     */
    private String headerEncoding;

    /**
     * The progress listener.
     */
    private ProgressListener listener;

    /**
     * Gets the boundary from the {@code Content-type} header.
     *
     * @param contentType The value of the content type header from which to extract the boundary value.
     * @return The boundary, as a byte array.
     */
    public byte[] getBoundary(final String contentType) {
        final ParameterParser parser = new ParameterParser();
        parser.setLowerCaseNames(true);
        // Parameter parser can handle null input
        final Map<String, String> params = parser.parse(contentType, new char[] { ';', ',' });
        final String boundaryStr = params.get("boundary");

        if (boundaryStr == null) {
            return null;
        }
        final byte[] boundary;
        boundary = boundaryStr.getBytes(StandardCharsets.ISO_8859_1);
        return boundary;
    }

    /**
     * Gets the field name from the {@code Content-disposition} header.
     *
     * @param headers A {@code Map} containing the HTTP request headers.
     * @return The field name for the current {@code encapsulation}.
     */
    public String getFieldName(final FileItemHeaders headers) {
        return getFieldName(headers.getHeader(CONTENT_DISPOSITION));
    }

    /**
     * Gets the field name, which is given by the content-disposition header.
     *
     * @param contentDisposition The content-dispositions header value.
     * @return The field jake
     */
    private String getFieldName(final String contentDisposition) {
        String fieldName = null;
        if (contentDisposition != null && contentDisposition.toLowerCase(Locale.ENGLISH).startsWith(FORM_DATA)) {
            final ParameterParser parser = new ParameterParser();
            parser.setLowerCaseNames(true);
            // Parameter parser can handle null input
            final Map<String, String> params = parser.parse(contentDisposition, ';');
            fieldName = params.get("name");
            if (fieldName != null) {
                fieldName = fieldName.trim();
            }
        }
        return fieldName;
    }

    /**
     * Gets the maximum number of files allowed in a single request.
     *
     * @return The maximum number of files allowed in a single request.
     */
    public long getFileCountMax() {
        return fileCountMax;
    }

    /**
     * Gets the factory class used when creating file items.
     *
     * @return The factory class for new file items.
     */
    public abstract FileItemFactory getFileItemFactory();

    /**
     * Gets the file name from the {@code Content-disposition} header.
     *
     * @param headers The HTTP headers object.
     *
     * @return The file name for the current {@code encapsulation}.
     */
    public String getFileName(final FileItemHeaders headers) {
        return getFileName(headers.getHeader(CONTENT_DISPOSITION));
    }

    /**
     * Gets the given content-disposition headers file name.
     *
     * @param contentDisposition The content-disposition headers value.
     * @return The file name
     */
    private String getFileName(final String contentDisposition) {
        String fileName = null;
        if (contentDisposition != null) {
            final String cdl = contentDisposition.toLowerCase(Locale.ENGLISH);
            if (cdl.startsWith(FORM_DATA) || cdl.startsWith(ATTACHMENT)) {
                final ParameterParser parser = new ParameterParser();
                parser.setLowerCaseNames(true);
                // Parameter parser can handle null input
                final Map<String, String> params = parser.parse(contentDisposition, ';');
                if (params.containsKey("filename")) {
                    fileName = params.get("filename");
                    if (fileName != null) {
                        fileName = fileName.trim();
                    } else {
                        // Even if there is no value, the parameter is present,
                        // so we return an empty file name rather than no file
                        // name.
                        fileName = "";
                    }
                }
            }
        }
        return fileName;
    }

    /**
     * Gets the maximum allowed size of a single uploaded file, as opposed to {@link #getSizeMax()}.
     *
     * @see #setFileSizeMax(long)
     * @return Maximum size of a single uploaded file.
     */
    public long getFileSizeMax() {
        return fileSizeMax;
    }

    /**
     * Gets the character encoding used when reading the headers of an individual part. When not specified, or {@code null}, the request encoding is used. If
     * that is also not specified, or {@code null}, the platform default encoding is used.
     *
     * @return The encoding used to read part headers.
     */
    public String getHeaderEncoding() {
        return headerEncoding;
    }

    /**
     * Gets an <a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a> compliant {@code multipart/form-data} stream.
     *
     * @param ctx The context for the request to be parsed.
     * @return An iterator to instances of {@code FileItemStream} parsed from the request, in the order that they were transmitted.
     * @throws FileUploadException if there are problems reading/parsing the request or storing files.
     * @throws IOException         An I/O error occurred. This may be a network error while communicating with the client or a problem while storing the
     *                             uploaded content.
     */
    public FileItemIterator getItemIterator(final RequestContext ctx) throws FileUploadException, IOException {
        return new FileItemIteratorImpl(this, ctx);
    }

    /**
     * Parses the {@code header-part} and returns as key/value pairs.
     * <p>
     * If there are multiple headers of the same names, the name will map to a comma-separated list containing the values.
     * </p>
     *
     * @param headerPart The {@code header-part} of the current {@code encapsulation}.
     * @return A {@code Map} containing the parsed HTTP request headers.
     */
    public FileItemHeaders getParsedHeaders(final String headerPart) {
        final int len = headerPart.length();
        final FileItemHeadersImpl headers = newFileItemHeaders();
        int start = 0;
        for (;;) {
            int end = parseEndOfLine(headerPart, start);
            if (start == end) {
                break;
            }
            final StringBuilder header = new StringBuilder(headerPart.substring(start, end));
            start = end + 2;
            while (start < len) {
                int nonWs = start;
                while (nonWs < len) {
                    final char c = headerPart.charAt(nonWs);
                    if (c != ' ' && c != '\t') {
                        break;
                    }
                    ++nonWs;
                }
                if (nonWs == start) {
                    break;
                }
                // Continuation line found
                end = parseEndOfLine(headerPart, nonWs);
                header.append(' ').append(headerPart, nonWs, end);
                start = end + 2;
            }
            parseHeaderLine(headers, header.toString());
        }
        return headers;
    }

    /**
     * Gets the progress listener.
     *
     * @return The progress listener, if any, or null.
     */
    public ProgressListener getProgressListener() {
        return listener;
    }

    /**
     * Gets the maximum allowed size of a complete request, as opposed to {@link #getFileSizeMax()}.
     *
     * @return The maximum allowed size, in bytes. The default value of -1 indicates, that there is no limit.
     * @see #setSizeMax(long)
     *
     */
    public long getSizeMax() {
        return sizeMax;
    }

    /**
     * Creates a new instance of {@link FileItemHeaders}.
     *
     * @return The new instance.
     */
    protected FileItemHeadersImpl newFileItemHeaders() {
        return new FileItemHeadersImpl();
    }

    /**
     * Skips bytes until the end of the current line.
     *
     * @param headerPart The headers, which are being parsed.
     * @param end        Index of the last byte, which has yet been processed.
     * @return Index of the \r\n sequence, which indicates end of line.
     */
    private int parseEndOfLine(final String headerPart, final int end) {
        int index = end;
        for (;;) {
            final int offset = headerPart.indexOf('\r', index);
            if (offset == -1 || offset + 1 >= headerPart.length()) {
                throw new IllegalStateException("Expected headers to be terminated by an empty line.");
            }
            if (headerPart.charAt(offset + 1) == '\n') {
                return offset;
            }
            index = offset + 1;
        }
    }

    /**
     * Parses the next header line.
     *
     * @param headers String with all headers.
     * @param header  Map where to store the current header.
     */
    private void parseHeaderLine(final FileItemHeadersImpl headers, final String header) {
        final int colonOffset = header.indexOf(':');
        if (colonOffset == -1) {
            // This header line is malformed, skip it.
            return;
        }
        final String headerName = header.substring(0, colonOffset).trim();
        final String headerValue = header.substring(colonOffset + 1).trim();
        headers.addHeader(headerName, headerValue);
    }

    /**
     * Parses an <a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a> compliant {@code multipart/form-data} stream.
     *
     * @param ctx The context for the request to be parsed.
     * @return A map of {@code FileItem} instances parsed from the request.
     * @throws FileUploadException if there are problems reading/parsing the request or storing files.
     */
    public Map<String, List<FileItem>> parseParameterMap(final RequestContext ctx) throws FileUploadException {
        final List<FileItem> items = parseRequest(ctx);
        final Map<String, List<FileItem>> itemsMap = new HashMap<>(items.size());

        for (final FileItem fileItem : items) {
            final String fieldName = fileItem.getFieldName();
            final List<FileItem> mappedItems = itemsMap.computeIfAbsent(fieldName, k -> new ArrayList<>());

            mappedItems.add(fileItem);
        }

        return itemsMap;
    }

    /**
     * Parses an <a href="http://www.ietf.org/rfc/rfc1867.txt">RFC 1867</a> compliant {@code multipart/form-data} stream.
     *
     * @param ctx The context for the request to be parsed.
     * @return A list of {@code FileItem} instances parsed from the request, in the order that they were transmitted.
     * @throws FileUploadException if there are problems reading/parsing the request or storing files.
     */
    public List<FileItem> parseRequest(final RequestContext ctx) throws FileUploadException {
        final List<FileItem> items = new ArrayList<>();
        boolean successful = false;
        try {
            final FileItemIterator iter = getItemIterator(ctx);
            final FileItemFactory fileItemFactory = Objects.requireNonNull(getFileItemFactory(), "No FileItemFactory has been set.");
            final byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
            while (iter.hasNext()) {
                if (items.size() == fileCountMax) {
                    // The next item will exceed the limit.
                    throw new FileUploadFileCountLimitException(ATTACHMENT, getFileCountMax(), items.size());
                }
                final FileItemStream item = iter.next();
                // Don't use getName() here to prevent an InvalidFileNameException.
                final String fileName = item.getName();
                final FileItem fileItem = fileItemFactory.createItem(item.getFieldName(), item.getContentType(), item.isFormField(), fileName);
                items.add(fileItem);
                try (InputStream inputStream = item.openStream();
                        OutputStream outputStream = fileItem.getOutputStream()) {
                    IOUtils.copyLarge(inputStream, outputStream, buffer);
                } catch (final FileUploadException e) {
                    throw e;
                } catch (final IOException e) {
                    throw new FileUploadException(String.format("Processing of %s request failed. %s", MULTIPART_FORM_DATA, e.getMessage()), e);
                }
                fileItem.setHeaders(item.getHeaders());
            }
            successful = true;
            return items;
        } catch (final FileUploadException e) {
            throw e;
        } catch (final IOException e) {
            throw new FileUploadException(e.getMessage(), e);
        } finally {
            if (!successful) {
                for (final FileItem fileItem : items) {
                    try {
                        fileItem.delete();
                    } catch (final Exception ignored) {
                        // ignored TODO perhaps add to tracker delete failure list somehow?
                    }
                }
            }
        }
    }

    /**
     * Sets the maximum number of files allowed per request.
     *
     * @param fileCountMax The new limit. {@code -1} means no limit.
     */
    public void setFileCountMax(final long fileCountMax) {
        this.fileCountMax = fileCountMax;
    }

    /**
     * Sets the factory class to use when creating file items.
     *
     * @param factory The factory class for new file items.
     */
    public abstract void setFileItemFactory(FileItemFactory factory);

    /**
     * Sets the maximum allowed size of a single uploaded file, as opposed to {@link #getSizeMax()}.
     *
     * @see #getFileSizeMax()
     * @param fileSizeMax Maximum size of a single uploaded file.
     */
    public void setFileSizeMax(final long fileSizeMax) {
        this.fileSizeMax = fileSizeMax;
    }

    /**
     * Specifies the character encoding to be used when reading the headers of individual part. When not specified, or {@code null}, the request encoding is
     * used. If that is also not specified, or {@code null}, the platform default encoding is used.
     *
     * @param encoding The encoding used to read part headers.
     */
    public void setHeaderEncoding(final String encoding) {
        headerEncoding = encoding;
    }

    /**
     * Sets the progress listener.
     *
     * @param listener The progress listener, if any. Defaults to null.
     */
    public void setProgressListener(final ProgressListener listener) {
        this.listener = listener;
    }

    /**
     * Sets the maximum allowed size of a complete request, as opposed to {@link #setFileSizeMax(long)}.
     *
     * @param sizeMax The maximum allowed size, in bytes. The default value of -1 indicates, that there is no limit.
     * @see #getSizeMax()
     */
    public void setSizeMax(final long sizeMax) {
        this.sizeMax = sizeMax;
    }

}
