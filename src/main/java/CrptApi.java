import org.apache.http.HttpEntity; // представляет сущность HTTP (запрос или ответ).
import org.apache.http.client.methods.CloseableHttpResponse; // представляет ответ HTTP и может быть закрыт.
import org.apache.http.client.methods.HttpPost; // представляет HTTP POST-запрос.
import org.apache.http.entity.StringEntity; //представляет строку как содержимое сущности HTTP.
import org.apache.http.impl.client.CloseableHttpClient; //представляет клиент HTTP, который может быть закрыт.
import org.apache.http.impl.client.HttpClients; // создает экземпляры CloseableHttpClient.
import org.apache.http.util.EntityUtils; //утилиты для работы с объектами HttpEntity.
import com.google.gson.Gson; // используется для преобразования Java объектов в JSON и обратно
import com.google.gson.JsonElement; // представляет элемент JSON.
import com.google.gson.JsonObject; //  представляет объект JSON.
import com.google.gson.JsonParser; // используется для парсинга JSON-строк в объекты JsonElement.

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