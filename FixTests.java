import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class FixTests3 {
    public static void main(String[] args) throws IOException {
        Path root = Paths.get("c:\\Users\\manis\\OneDrive\\Desktop\\LemonAcademy\\E-Commerce\\backend\\src\\test");
        
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.toString().endsWith(".java")) return FileVisitResult.CONTINUE;
                
                String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                String original = content;
                
                // 1. Replace anyLong() with any(UUID.class) for Mockito matchers
                content = content.replace("anyLong()", "any(UUID.class)");
                
                // 2. Fix DashboardResponse counts — these should be long, not UUID
                // Pattern: .totalUsers(UUID.fromString(...)) -> .totalUsers(10L) etc
                content = content.replace(".totalUsers(UUID.fromString(\"0bbc4ab8-e7c0-3e38-88c8-59fd4801d7b4\"))", ".totalUsers(10L)");
                content = content.replace(".totalProducts(UUID.fromString(\"d298ac07-c942-36a6-9bdf-313a60e5eceb\"))", ".totalProducts(25L)");
                content = content.replace(".totalOrders(UUID.fromString(\"f6b94ab3-a544-3f41-a168-f01ee2e33f09\"))", ".totalOrders(50L)");
                content = content.replace(".pendingOrders(UUID.fromString(\"e421704c-0629-3dd7-864a-e971853c032f\"))", ".pendingOrders(5L)");
                content = content.replace(".deliveredOrders(UUID.fromString(\"4ce58a98-b5d7-3199-b631-501303d42047\"))", ".deliveredOrders(30L)");
                content = content.replace(".cancelledOrders(UUID.fromString(\"270d9312-e37e-362b-b824-92a35f14593a\"))", ".cancelledOrders(3L)");
                
                // Fix assertion values too  
                content = content.replace("isEqualTo(UUID.fromString(\"0bbc4ab8-e7c0-3e38-88c8-59fd4801d7b4\"))", "isEqualTo(10L)");
                content = content.replace("isEqualTo(UUID.fromString(\"d298ac07-c942-36a6-9bdf-313a60e5eceb\"))", "isEqualTo(25L)");
                content = content.replace("isEqualTo(UUID.fromString(\"f6b94ab3-a544-3f41-a168-f01ee2e33f09\"))", "isEqualTo(50L)");
                content = content.replace("isEqualTo(UUID.fromString(\"e421704c-0629-3dd7-864a-e971853c032f\"))", "isEqualTo(5L)");
                content = content.replace("isEqualTo(UUID.fromString(\"4ce58a98-b5d7-3199-b631-501303d42047\"))", "isEqualTo(30L)");
                content = content.replace("isEqualTo(UUID.fromString(\"270d9312-e37e-362b-b824-92a35f14593a\"))", "isEqualTo(3L)");
                
                // Add UUID import if needed
                if (content.contains("UUID") && !content.contains("import java.util.UUID;")) {
                    content = content.replaceFirst("(package [^;]+;)", "$1\n\nimport java.util.UUID;");
                }
                
                if (!content.equals(original)) {
                    Files.write(file, content.getBytes(StandardCharsets.UTF_8));
                    System.out.println("Updated " + file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
