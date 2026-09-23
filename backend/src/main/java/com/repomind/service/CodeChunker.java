package com.repomind.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;


@Service
public class CodeChunker {

    private static final Logger log = LoggerFactory.getLogger(CodeChunker.class);

    
    private static final int MAX_CHUNK_SIZE = 1500;

    
    private static final int CHUNK_OVERLAP = 200;

    
    private static final Set<String> SKIP_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".svg", ".ico", ".bmp", ".webp",
            ".woff", ".woff2", ".ttf", ".eot", ".otf",
            ".mp3", ".mp4", ".avi", ".mov", ".wav",
            ".zip", ".tar", ".gz", ".rar", ".7z",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx",
            ".exe", ".dll", ".so", ".dylib", ".class", ".jar",
            ".min.js", ".min.css",
            ".lock", ".map"
    );

    
    private static final Set<String> SKIP_DIRECTORIES = Set.of(
            "node_modules", ".git", ".svn", ".hg", "dist", "build", "target",
            "__pycache__", ".pytest_cache", ".next", ".nuxt", "vendor",
            ".idea", ".vscode", ".settings", "coverage", ".nyc_output"
    );

    
    private static final Set<String> CODE_EXTENSIONS = Set.of(
            ".java", ".py", ".js", ".ts", ".jsx", ".tsx", ".go", ".rs",
            ".cpp", ".c", ".h", ".hpp", ".cs", ".rb", ".php", ".swift",
            ".kt", ".scala", ".r", ".m", ".mm", ".lua", ".pl", ".pm",
            ".html", ".css", ".scss", ".sass", ".less",
            ".json", ".yml", ".yaml", ".xml", ".toml", ".ini", ".cfg",
            ".md", ".txt", ".rst",
            ".sql", ".sh", ".bash", ".zsh", ".ps1", ".bat", ".cmd",
            ".dockerfile", ".tf", ".hcl", ".proto", ".graphql", ".gql"
    );

    
    public boolean shouldIndex(String filePath) {
        String lowerPath = filePath.toLowerCase();

        
        for (String skipDir : SKIP_DIRECTORIES) {
            if (lowerPath.contains("/" + skipDir + "/") || lowerPath.startsWith(skipDir + "/")) {
                return false;
            }
        }

        
        for (String ext : SKIP_EXTENSIONS) {
            if (lowerPath.endsWith(ext)) {
                return false;
            }
        }

        
        for (String ext : CODE_EXTENSIONS) {
            if (lowerPath.endsWith(ext)) {
                return true;
            }
        }

        
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1).toLowerCase();
        return fileName.equals("dockerfile") || fileName.equals("makefile") ||
               fileName.equals(".gitignore") || fileName.equals(".env.example");
    }

    
    public List<ChunkResult> chunkFile(String filePath, String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }

        
        if (content.length() > 500_000) {
            log.warn("Skipping very large file: {} ({} chars)", filePath, content.length());
            return Collections.emptyList();
        }

        String language = detectLanguage(filePath);
        List<ChunkResult> chunks = new ArrayList<>();

        
        if (content.length() <= MAX_CHUNK_SIZE) {
            chunks.add(new ChunkResult(
                    content,
                    filePath,
                    language,
                    1,
                    countLines(content),
                    0
            ));
            return chunks;
        }

        
        String[] lines = content.split("\n");
        StringBuilder currentChunk = new StringBuilder();
        int chunkStartLine = 1;
        int currentLine = 1;
        int chunkIndex = 0;

        for (String line : lines) {
            if (currentChunk.length() + line.length() + 1 > MAX_CHUNK_SIZE && currentChunk.length() > 0) {
                
                chunks.add(new ChunkResult(
                        currentChunk.toString(),
                        filePath,
                        language,
                        chunkStartLine,
                        currentLine - 1,
                        chunkIndex++
                ));

                
                String chunkStr = currentChunk.toString();
                String[] chunkLines = chunkStr.split("\n");
                currentChunk = new StringBuilder();
                int overlapLines = Math.min(chunkLines.length, 5);
                int overlapStart = chunkLines.length - overlapLines;
                chunkStartLine = currentLine - overlapLines;

                for (int i = overlapStart; i < chunkLines.length; i++) {
                    currentChunk.append(chunkLines[i]).append("\n");
                }
            }

            currentChunk.append(line).append("\n");
            currentLine++;
        }

        
        if (currentChunk.length() > 0) {
            chunks.add(new ChunkResult(
                    currentChunk.toString(),
                    filePath,
                    language,
                    chunkStartLine,
                    currentLine - 1,
                    chunkIndex
            ));
        }

        return chunks;
    }

    
    public String detectLanguage(String filePath) {
        String lowerPath = filePath.toLowerCase();
        if (lowerPath.endsWith(".java")) return "java";
        if (lowerPath.endsWith(".py")) return "python";
        if (lowerPath.endsWith(".js")) return "javascript";
        if (lowerPath.endsWith(".ts")) return "typescript";
        if (lowerPath.endsWith(".jsx")) return "jsx";
        if (lowerPath.endsWith(".tsx")) return "tsx";
        if (lowerPath.endsWith(".go")) return "go";
        if (lowerPath.endsWith(".rs")) return "rust";
        if (lowerPath.endsWith(".cpp") || lowerPath.endsWith(".c")) return "c/c++";
        if (lowerPath.endsWith(".cs")) return "csharp";
        if (lowerPath.endsWith(".rb")) return "ruby";
        if (lowerPath.endsWith(".php")) return "php";
        if (lowerPath.endsWith(".swift")) return "swift";
        if (lowerPath.endsWith(".kt")) return "kotlin";
        if (lowerPath.endsWith(".html")) return "html";
        if (lowerPath.endsWith(".css") || lowerPath.endsWith(".scss")) return "css";
        if (lowerPath.endsWith(".sql")) return "sql";
        if (lowerPath.endsWith(".sh") || lowerPath.endsWith(".bash")) return "shell";
        if (lowerPath.endsWith(".yml") || lowerPath.endsWith(".yaml")) return "yaml";
        if (lowerPath.endsWith(".json")) return "json";
        if (lowerPath.endsWith(".xml")) return "xml";
        if (lowerPath.endsWith(".md")) return "markdown";
        return "text";
    }

    private int countLines(String content) {
        return (int) content.lines().count();
    }

    
    public record ChunkResult(
            String content,
            String filePath,
            String language,
            int startLine,
            int endLine,
            int chunkIndex
    ) {}
}
