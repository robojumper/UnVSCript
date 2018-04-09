import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

class ExampleLanguageServer implements LanguageServer, LanguageClientAware {

    private LanguageClient client = null;

    @SuppressWarnings("unused")
    private String workspaceRoot = null;
    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        System.out.println("initialize");
        workspaceRoot = params.getRootPath();

        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
        capabilities.setCodeActionProvider(false);
        capabilities.setCompletionProvider(new CompletionOptions(true, null));

        return CompletableFuture.completedFuture(new InitializeResult(capabilities));
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        System.out.println("shutdown");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        System.out.println("exit");
    }

    private FullTextDocumentService fullTextDocumentService = new FullTextDocumentService() {

        @Override
        public CompletableFuture<CompletionList> completion(TextDocumentPositionParams textDocumentPosition) {
            System.out.println("completion");
            CompletionItem typescriptCompletionItem = new CompletionItem();
            typescriptCompletionItem.setLabel("TypeScript");
            typescriptCompletionItem.setKind(CompletionItemKind.Text);
            typescriptCompletionItem.setData(1.0);

            CompletionItem javascriptCompletionItem = new CompletionItem();
            javascriptCompletionItem.setLabel("JavaScript");
            javascriptCompletionItem.setKind(CompletionItemKind.Text);
            javascriptCompletionItem.setData(2.0);

            List<CompletionItem> completions = new ArrayList<>();
            completions.add(typescriptCompletionItem);
            completions.add(javascriptCompletionItem);

            return CompletableFuture.completedFuture(new CompletionList(false, completions));
        }

        @Override
        public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem item) {
            System.out.println("resolveCompletionItem");
            if (item.getData().equals(1.0)) {
                item.setDetail("TypeScript details");
                item.setDocumentation("TypeScript documentation");
            } else if (item.getData().equals(2.0)) {
                item.setDetail("JavaScript details");
                item.setDocumentation("JavaScript documentation");
            }
            return CompletableFuture.completedFuture(item);
        }

        @Override
        public void didChange(DidChangeTextDocumentParams params) {
            System.out.println("didChange");
            super.didChange(params);

            TextDocumentItem document = this.documents.get(params.getTextDocument().getUri());
            validateDocument(document);
        }
    };

    @Override
    public TextDocumentService getTextDocumentService() {
        System.out.println("getTextDocumentService");
        return fullTextDocumentService;
    }

    private void validateDocument(TextDocumentItem document) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        String[] lines = document.getText().split("\\r?\\n");
        int problems = 0;
        for (int i = 0; i < lines.length && problems < maxNumberOfProblems; i++) {
            String line = lines[i];
            int index = line.indexOf("typescript");
            if (index >= 0) {
                problems++;
                Diagnostic diagnostic = new Diagnostic();
                diagnostic.setSeverity(DiagnosticSeverity.Warning);
                diagnostic.setRange(new Range(new Position(i, index), new Position(i, index + 10)));
                diagnostic.setMessage(String.format("%s should be spelled TypeScript", line.substring(index, index + 10)));
                diagnostic.setSource("ex");
                diagnostics.add(diagnostic);
            }
        }

        client.publishDiagnostics(new PublishDiagnosticsParams(document.getUri(), diagnostics));
    }

    private int maxNumberOfProblems = 100;

    @Override
    public WorkspaceService getWorkspaceService() {
        System.out.println("getWorkspaceService");
        return new WorkspaceService() {
            @Override
            public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
                System.out.println("symbol");
                return null;
            }

            @Override
            public void didChangeConfiguration(DidChangeConfigurationParams params) {
                System.out.println("didChangeConfiguration");
                Map<String, Object> settings = (Map<String, Object>) params.getSettings();
                Map<String, Object> unvscriptLS = (Map<String, Object>) settings.get("unvscriptLS");
                maxNumberOfProblems = ((Double)unvscriptLS.getOrDefault("maxNumberOfProblems", 100.0)).intValue();
                fullTextDocumentService.documents.values().forEach(d -> validateDocument(d));
            }

            @Override
            public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
                System.out.println("didChangeWatchedFiles");
                client.logMessage(new MessageParams(MessageType.Log, "We received an file change event"));
            }
        };
    }

    @Override
    public void connect(LanguageClient client) {
        System.out.println("connect");
        this.client = client;
    }

}