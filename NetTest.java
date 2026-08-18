import java.net.HttpURLConnection;
import java.net.URL;

/** Fetches one Bing tile exactly as WorldWind would, to see whether the JVM can reach the server. */
public class NetTest
{
    public static void main(String[] args) throws Exception
    {
        String tile = "https://worldwind27.arc.nasa.gov/wms/virtualearth"
            + "?service=WMS&request=GetMap&version=1.1.1&srs=EPSG:4326"
            + "&layers=ve&styles=&transparent=TRUE&format=image/png"
            + "&width=512&height=512&bbox=32.0,38.0,32.25,38.25";

        System.out.println("java.version      = " + System.getProperty("java.version"));
        System.out.println("java.vendor       = " + System.getProperty("java.vendor"));
        System.out.println("proxyHost         = " + System.getProperty("https.proxyHost"));
        System.out.println("useSystemProxies  = " + System.getProperty("java.net.useSystemProxies"));
        System.out.println();

        HttpURLConnection c = (HttpURLConnection) new URL(tile).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        System.out.println("HTTP " + c.getResponseCode() + " " + c.getResponseMessage());
        System.out.println("Content-Type: " + c.getContentType());
        System.out.println("Length:       " + c.getContentLength() + " byte");
        c.disconnect();
        System.out.println();
        System.out.println("SONUC: JVM sunucuya ULASTI.");
    }
}
