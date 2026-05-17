package com.recallmaster.universal.document;

import com.recallmaster.universal.model.DocumentChunk;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class DocumentLoader {

    private static final int DEFAULT_CHUNK_SIZE = 800;
    private static final int DEFAULT_OVERLAP = 120;

    public List<DocumentChunk> load(Path path) {
        try {
            String text = switch (extension(path)) {
                case "pdf" -> readPdf(path);
                case "md", "txt" -> Files.readString(path);
                default -> throw new IllegalArgumentException("Unsupported document format: " + path);
            };
            return chunk(path, text);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load document " + path, ex);
        }
    }

    private List<DocumentChunk> chunk(Path path, String text) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String normalized = text == null ? "" : text.replace("\r\n", "\n").trim();
        int index = 0;
        int chunkNo = 1;
        while (index < normalized.length()) {
            int end = Math.min(normalized.length(), index + DEFAULT_CHUNK_SIZE);
            String piece = normalized.substring(index, end).trim();
            if (!piece.isBlank()) {
                Map<String, String> metadata = new LinkedHashMap<>();
                metadata.put("source", path.toString());
                metadata.put("chunk", Integer.toString(chunkNo));
                chunks.add(new DocumentChunk(path.getFileName() + "#" + chunkNo, piece, metadata));
                chunkNo++;
            }
            if (end >= normalized.length()) {
                break;
            }
            index = Math.max(end - DEFAULT_OVERLAP, index + 1);
        }
        return chunks;
    }

    private String readPdf(Path path) throws IOException {
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extension(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }
}
