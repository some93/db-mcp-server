// MCP Tools Definition

package com.mcp.transport;

/**
 * This class defines shared MCP tools definitions and utility methods
 * for both standard input/output (stdio) and Server-Sent Events (SSE) transport layers.
 */
public class McpToolsDefinition {

    /**
     * Utility method to log messages to the standard output.
     *
     * @param message The message to log.
     */
    public static void log(String message) {
        System.out.println(message);
    }

    /**
     * Utility method to handle SSE connection states.
     *
     * @param event The SSE event to handle.
     */
    public static void handleSseEvent(String event) {
        // Logic to handle the event
        System.out.println("Received SSE Event: " + event);
    }

    // Add more utility methods as required for the MCP tools
}