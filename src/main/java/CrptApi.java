import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileReader;
import java.io.IOException;

import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class CrptApi {

    private final TimeUnit timeUnit;
    private final int requestLimit;

    private final BlockingQueue<Long> requestTimestamps;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.requestTimestamps = new ArrayBlockingQueue<>(requestLimit);
    }

    public synchronized void createDocument(String documentJson, String signature) {
        try {
            long currentTime = Instant.now().toEpochMilli();
            if (!canMakeRequest(currentTime)) {
                System.out.println("Request limit exceeded. Waiting for next available request slot.");
            }

            String url = "https://ismp.crpt.ru/api/v3/lk/documents/create";
            try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
                HttpPost httpPost = new HttpPost(url);
                httpPost.setEntity(new StringEntity(documentJson));

                try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                    HttpEntity responseEntity = response.getEntity();
                    System.out.println(EntityUtils.toString(responseEntity));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private synchronized boolean canMakeRequest(long currentTime) throws InterruptedException {
        long oldestRequestTime = currentTime - timeUnit.toMillis(1);
        requestTimestamps.removeIf(time -> time < oldestRequestTime);

        if (requestTimestamps.size() < requestLimit) {
            requestTimestamps.add(currentTime);
            return true;
        } else {
            long timeToWait = requestTimestamps.element() + timeUnit.toMillis(1) - currentTime;
            if (timeToWait > 0) {
                wait(timeToWait);
            }
            return false;
        }
    }

    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 10);
        String signature = "signature";
        Gson gson = new Gson();
        try (FileReader reader = new FileReader("src/main/resources/document.json")) {
            JsonElement jsonElement = JsonParser.parseReader(reader);
            JsonObject jsonObject = jsonElement.getAsJsonObject();
            crptApi.createDocument(jsonObject.toString(), signature);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}