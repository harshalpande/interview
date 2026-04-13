$body = @{
    sourceCode = @"
import static org.junit.Assert.*;
public class Solution {
    public static void main(String[] args) {
        assertTrue(true);
        System.out.println("JUnit works!");
    }
"@
    timeoutMs = 5000
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/execute" -Method Post -ContentType "application/json" -Body $body
