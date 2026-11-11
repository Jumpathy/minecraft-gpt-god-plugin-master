package net.bigyous.gptgodmc.god.llm;

import java.util.function.Supplier;

/** What one streamed request amounted to, in terms every provider shares. */
public final class LlmReply {
    public enum Stop {
        END_TURN, TOOL_USE, PAUSE_TURN, REFUSAL, MAX_TOKENS, OTHER
    }

    /** Why a request failed. */
    public record Error(Kind kind, int status, String body, String message) {
        public enum Kind {
            /** The server answered with an error status. */
            SERVICE,
            /** The server could not be reached, or the connection dropped. */
            NETWORK,
            /** The reply broke off for another reason. */
            BROKEN
        }

        public static Error service(int status, String body) {
            return new Error(Kind.SERVICE, status, body, status + ": " + body);
        }

        public static Error network(String message) {
            return new Error(Kind.NETWORK, 0, null, message);
        }

        public static Error broken(String message) {
            return new Error(Kind.BROKEN, 0, null, message);
        }
    }

    public Stop stop = Stop.END_TURN;
    /** The provider's own word for the stop reason, for logs and the mind viewer. */
    public String stopText = "end_turn";
    /** The reply arrived whole. */
    public boolean finished;
    /** The provider reported usage for this request. */
    public boolean billed;
    /** `in` is uncached input only; cache reads and writes are counted apart. */
    public long in, out, cacheRead, cacheWrite, searches;
    /** Null when the request went through. */
    public Error error;
    /** The model's turn can be sent back in the next request. */
    public boolean replayable;
    /** " (category)" for a refusal the provider explained, else "". */
    public String refusalDetail = "";
    /** The whole reply as JSON for the webhook and mind viewer; null when there is none. */
    public Supplier<String> responseJson;
    /** The provider's own form of the turn (only the chat that made it reads this). */
    public Object turn;
}
