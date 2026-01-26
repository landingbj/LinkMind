/*
* Copyright 2024 - 2024 the original author or authors.
*/
package ai.mcps.client.transport;

import ai.mcps.util.Utils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.hc.client5.http.async.methods.AbstractCharResponseConsumer;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Method;

import java.net.URI;
import java.nio.CharBuffer;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;


public class FlowSseClient {

	private final CloseableHttpAsyncClient httpClient;

	/**
	 * Pattern to extract the data content from SSE data field lines. Matches lines
	 * starting with "data:" and captures the remaining content.
	 */
	private static final Pattern EVENT_DATA_PATTERN = Pattern.compile("^data:(.+)$", Pattern.MULTILINE);

	/**
	 * Pattern to extract the event ID from SSE id field lines. Matches lines starting
	 * with "id:" and captures the ID value.
	 */
	private static final Pattern EVENT_ID_PATTERN = Pattern.compile("^id:(.+)$", Pattern.MULTILINE);

	/**
	 * Pattern to extract the event type from SSE event field lines. Matches lines
	 * starting with "event:" and captures the event type.
	 */
	private static final Pattern EVENT_TYPE_PATTERN = Pattern.compile("^event:(.+)$", Pattern.MULTILINE);

	/**
	 * Record class representing a Server-Sent Event with its standard fields.
	 *
	 *  id the event ID (may be null)
	 *  type the event type (defaults to "message" if not specified in the stream)
	 *  data the event payload data
	 */
	@Data
	@AllArgsConstructor
	@NoArgsConstructor
	public static class SseEvent {
		String id; String type; String data;
	}

	/**
	 * Interface for handling SSE events and errors. Implementations can process received
	 * events and handle any errors that occur during the SSE connection.
	 */
	public interface SseEventHandler {

		/**
		 * Called when an SSE event is received.
		 * @param event the received SSE event containing id, type, and data
		 */
		void onEvent(SseEvent event);

		/**
		 * Called when an error occurs during the SSE connection.
		 * @param error the error that occurred
		 */
		void onError(Throwable error);

	}

	/**
	 * Creates a new FlowSseClient with the specified HTTP client.
	 * @param httpClient the {@link }HttpClient instance to use for SSE connections
	 */
	public FlowSseClient(CloseableHttpAsyncClient httpClient) {
		this.httpClient = httpClient;
	}

	/**
	 * Subscribes to an SSE endpoint and processes the event stream.
	 *
	 * <p>
	 * This method establishes a connection to the specified URL and begins processing the
	 * SSE stream. Events are parsed and delivered to the provided event handler. The
	 * connection remains active until either an error occurs or the server closes the
	 * connection.
	 * @param url the SSE endpoint URL to connect to
	 * @param eventHandler the handler that will receive SSE events and error
	 * notifications
	 * @throws RuntimeException if the connection fails with a non-200 status code
	 */
	public void subscribe(String url, SseEventHandler eventHandler) {
		var request = SimpleHttpRequest.create(Method.GET, URI.create(url));
		request.setHeader("Accept", "text/event-stream");
		request.setHeader("Cache-Control", "no-cache");

		StringBuilder eventBuilder = new StringBuilder();
		AtomicReference<String> currentEventId = new AtomicReference<>();
		AtomicReference<String> currentEventType = new AtomicReference<>("message");

//		Function<Flow.Subscriber<String>, HttpResponse.BodySubscriber<Void>> subscriberFactory = subscriber -> HttpResponse.BodySubscribers
//			.fromLineSubscriber(subscriber);

		var future = this.httpClient.execute(SimpleRequestProducer.create(request), new AbstractCharResponseConsumer<HttpResponse>() {
			final StringBuilder builder = new StringBuilder();
			HttpResponse httpResponse;

			@Override
			protected void start(
					final HttpResponse response,
					final ContentType contentType) {
				httpResponse = response;
			}

			@Override
			protected int capacityIncrement() {
				return Integer.MAX_VALUE;
			}

			@Override
			protected void data(final CharBuffer data, final boolean endOfStream) {
				while (data.hasRemaining()) {
					char c = data.get();
					if (c == '\n') {
						onLine(builder.toString());
						builder.setLength(0);
					} else {
						builder.append(c);
					}
				}
			}

			private void onLine(String line) {
				if (line.trim().isEmpty()) {
					// Empty line means end of event
					if (eventBuilder.length() > 0) {
						String eventData = eventBuilder.toString();
						SseEvent event = new SseEvent(currentEventId.get(), currentEventType.get(), eventData.trim());
						eventHandler.onEvent(event);
						eventBuilder.setLength(0);
					}
				} else {
					if (line.startsWith("data:")) {
						var matcher = EVENT_DATA_PATTERN.matcher(line);
						if (matcher.find()) {
							eventBuilder.append(matcher.group(1).trim()).append("\n");
						}
					}
					else if (line.startsWith("id:")) {
						var matcher = EVENT_ID_PATTERN.matcher(line);
						if (matcher.find()) {
							currentEventId.set(matcher.group(1).trim());
						}
					}
					else if (line.startsWith("event:")) {
						var matcher = EVENT_TYPE_PATTERN.matcher(line);
						if (matcher.find()) {
							currentEventType.set(matcher.group(1).trim());
						}
					}
				}
			}

			@Override
			protected HttpResponse buildResult() {
				return httpResponse;
			}

			@Override
			public void failed(final Exception cause) {
				System.out.println(request + "->" + cause);
			}

			@Override
			public void releaseResources() {
			}

		}, null);


		Utils.toCompletableFuture(future).thenAccept(response -> {
			int status = response.getCode();
			if (status != 200 && status != 201 && status != 202 && status != 206) {
				throw new RuntimeException("Failed to connect to SSE stream. Unexpected status code: " + status);
			}
		}).exceptionally(throwable -> {
			eventHandler.onError(throwable);
			return null;
		});
	}

}
