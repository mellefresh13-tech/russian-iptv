package ru.russianiptv.player;

import android.util.Xml;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;
import org.xmlpull.v1.XmlPullParser;

public final class EpgRepository {
    public static String nowAndNext(String epgUrl, String channelId) {
        if (epgUrl == null || epgUrl.isEmpty() || channelId == null || channelId.isEmpty()) return "";
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(epgUrl).openConnection();
            c.setConnectTimeout(10000); c.setReadTimeout(15000);
            InputStream input = c.getInputStream();
            if (epgUrl.endsWith(".gz")) input = new GZIPInputStream(input);
            XmlPullParser parser = Xml.newPullParser(); parser.setInput(input, "UTF-8");
            String currentChannel = ""; String title = ""; String start = ""; String stop = "";
            StringBuilder found = new StringBuilder();
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && "programme".equals(parser.getName())) {
                    currentChannel = parser.getAttributeValue(null, "channel");
                    start = parser.getAttributeValue(null, "start");
                    stop = parser.getAttributeValue(null, "stop");
                } else if (event == XmlPullParser.START_TAG && "title".equals(parser.getName())
                        && channelId.equals(currentChannel)) {
                    title = parser.nextText();
                    if (found.length() == 0) found.append("Сейчас: ").append(title);
                } else if (event == XmlPullParser.END_TAG && "programme".equals(parser.getName())) {
                    if (channelId.equals(currentChannel) && found.length() > 0) {
                        found.append("\nДалее: расписание доступно в EPG");
                        return found.toString();
                    }
                }
            }
            input.close(); return found.toString();
        } catch (Exception ignored) { return ""; }
    }
}
