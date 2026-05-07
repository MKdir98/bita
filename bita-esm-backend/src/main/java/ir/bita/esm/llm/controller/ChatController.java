package ir.bita.esm.llm.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.bita.esm.auth.security.CurrentUser;
import ir.bita.esm.llm.dto.*;
import ir.bita.esm.llm.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "LLM chat interface for service management")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create new chat session")
    public ResponseEntity<ChatSessionResponse> createSession(
            @CurrentUser Long userId,
            @RequestBody(required = false) CreateSessionRequest request) {
        if (request == null) {
            request = new CreateSessionRequest();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.createSession(userId, request));
    }

    @GetMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List user's chat sessions")
    public ResponseEntity<Page<ChatSessionResponse>> listSessions(
            @CurrentUser Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(chatService.getUserSessions(userId, pageable));
    }

    @GetMapping("/sessions/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get chat session with messages")
    public ResponseEntity<ChatSessionResponse> getSession(
            @PathVariable("sessionId") Long sessionId,
            @CurrentUser Long userId) {
        return ResponseEntity.ok(chatService.getSession(sessionId, userId));
    }

    @GetMapping("/sessions/{sessionId}/history")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get chat session message history")
    public ResponseEntity<ChatSessionResponse> getSessionHistory(
            @PathVariable("sessionId") Long sessionId,
            @CurrentUser Long userId) {
        return ResponseEntity.ok(chatService.getSession(sessionId, userId));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Send message to chat session")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @PathVariable("sessionId") Long sessionId,
            @CurrentUser Long userId,
            @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.ok(chatService.sendMessage(sessionId, userId, request));
    }

    @PostMapping("/sessions/{sessionId}/confirm")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Confirm or reject tool execution")
    public ResponseEntity<ChatMessageResponse> confirmTool(
            @PathVariable("sessionId") Long sessionId,
            @CurrentUser Long userId,
            @Valid @RequestBody ConfirmToolRequest request) {
        return ResponseEntity.ok(chatService.confirmTool(sessionId, userId, request));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Close chat session")
    public ResponseEntity<Void> closeSession(
            @PathVariable("sessionId") Long sessionId,
            @CurrentUser Long userId) {
        chatService.closeSession(sessionId, userId);
        return ResponseEntity.noContent().build();
    }
}
