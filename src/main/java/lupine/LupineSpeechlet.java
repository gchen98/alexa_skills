/** Copyright 2014-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.

    Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at

        http://aws.amazon.com/apache2.0/

    or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package lupine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.ArrayList;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.IntentRequest;
import com.amazon.speech.speechlet.LaunchRequest;
import com.amazon.speech.speechlet.Session;
import com.amazon.speech.speechlet.SessionEndedRequest;
import com.amazon.speech.speechlet.SessionStartedRequest;
import com.amazon.speech.speechlet.Speechlet;
import com.amazon.speech.speechlet.SpeechletException;
import com.amazon.speech.speechlet.SpeechletResponse;
import com.amazon.speech.ui.OutputSpeech;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.SsmlOutputSpeech;
import com.amazon.speech.ui.Reprompt;
import com.amazon.speech.ui.SimpleCard;

/**
 * This sample shows how to create a Lambda function for handling Alexa Skill requests that:
 * 
 * <ul>
 * <li><b>Web service</b>: communicate with an web service on Lupine to play media
 * <li><b>Dialog and Session state</b>: Handles a multi-turn dialog model</li>
 * <li><b>SSML</b>: Using SSML tags to control how Alexa renders the text-to-speech</li>
 * </ul>
 * <p>
 * <h2>Examples</h2>
 * <p>
 * <b>Dialog model</b>
 * <p>
 * User: "Alexa, ask Movie Player what shows are available"
 * <p>
 * Alexa: "The list of shows are Simpsons, Big Bang Theory, Last Man On Earth; What show would you like?"
 * <p>
 * User: "Simpsons"
 * <p>
 * Alexa: "There are 7 episodes to choose from, what episode would you like?"
 * <p>
 * User: "7"
 * <p>
 * Alexa: "Playing episode 7 for Simpsons"
 * <p>
 * User: "Skip 5 seconds"
 * <p>
 * Alexa: "Seeking 5 seconds for Simpsons"
 * <p>
 */
public class LupineSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(LupineSpeechlet.class);

    private static final String MPLAYER_WS_PREFIX =
    "http://www.caseyandgary.com:8000/mplayer/";
    private static final String BROWSER_WS_PREFIX =
    "http://www.caseyandgary.com:8000/browser/";

    private static final String SLOT_BOOKMARK = "bookmark";
    private static final String SLOT_SHOW = "show";
    private static final String SLOT_EPISODE = "episode";
    private static final String SLOT_SEEK_SECONDS = "seek_seconds";

    private static final String SESSION_SHOWS = "shows";
    private static final String SESSION_BOOKMARKS = "bookmarks";
    private static final String SESSION_SELECTED_SHOW = "selected_show";
    private static final String SESSION_EPISODES = "episodes";

    private static final String helpText = "With the video manager, you can ask for available shows, ask for available episodes, and play a particular episode. For example, you could say what shows are available, or what episodes are available, or play episode 4. So, what would you like to ask?";
    private static final String repromptText = "What would you like to ask?";

    enum MediaType{
        SHOW,BOOKMARK
    }

    @Override
    public void onSessionStarted(final SessionStartedRequest request, final Session session) throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", 
        request.getRequestId(), session.getSessionId());
        // any initialization logic goes here
    }

    @Override
    public SpeechletResponse onLaunch(final LaunchRequest request, final Session session) throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", request.getRequestId(),
        session.getSessionId());
        return newAskResponse(helpText, false, repromptText, false);
    }


    @Override
    public SpeechletResponse onIntent(final IntentRequest request, final Session session) throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", request.getRequestId(),
        session.getSessionId());

        Intent intent = request.getIntent();
        String intentName = intent.getName();

        if ("ListShowsIntent".equals(intentName) || 
        "ListBookmarksIntent".equals(intentName)) {
            MediaType mediaType = null;
            if("ListShowsIntent".equals(intentName)){
                mediaType = MediaType.SHOW;
            }else if("ListBookmarksIntent".equals(intentName)){
                mediaType = MediaType.BOOKMARK;
            }
            return handleListMedia(intent,session,mediaType);
        }else if ("PlayShowIntent".equals(intentName)) {
            return handlePlayShow(intent,session);
        }else if ("OpenBookmarkIntent".equals(intentName)) {
            return handleOpenBookmark(intent,session);
        }else if ("PlayEpisodeIntent".equals(intentName)) {
            return handlePlayEpisode(intent,session);
        }else if ("SeekSecondsIntent".equals(intentName)) {
            return handleSeekSeconds(intent,session);
        } else if ("AMAZON.HelpIntent".equals(intentName)) {
            // Create the plain text output.
            return newAskResponse(helpText, false, repromptText, false);
        } else if ("AMAZON.StopIntent".equals(intentName)) {
            return handleStopIntent(intent,session);
        } else if ("AMAZON.CancelIntent".equals(intentName)) {
            PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
            outputSpeech.setText("Goodbye");

            return SpeechletResponse.newTellResponse(outputSpeech);
        } else {
            throw new SpeechletException("Invalid Intent");
        }
    }

    @Override
    public void onSessionEnded(final SessionEndedRequest request, final Session session)
            throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", request.getRequestId(),
                session.getSessionId());

        // any session cleanup logic would go here
    }

    private SpeechletResponse handleListMedia(Intent intent, Session session,MediaType mediaType){
        try{

            String speechPrefixContent = "<p>Here is your list of programs</p> ";
            String cardPrefixContent = "Here is your list of programs ";
            String cardTitle = "Shows";
            String speechOutput = null;

            URL url = null;
            switch(mediaType){
                case SHOW:
                    url = new URL(MPLAYER_WS_PREFIX + "/list");
                    break;
                case BOOKMARK:
                    url = new URL(BROWSER_WS_PREFIX + "/list");
                    break;
            }
            String jsonText = getJsonString(url);
            List<String> mediaNames = getJsonMediaNames(jsonText,mediaType);
            if(mediaNames==null){
                speechOutput = "There was a problem connecting to the media server at this time. Please try again later.";
                // Create the plain text output
                SsmlOutputSpeech outputSpeech = new SsmlOutputSpeech();
                outputSpeech.setSsml("<speak>" + speechOutput + "</speak>");
                return SpeechletResponse.newTellResponse(outputSpeech);
            }else{
                StringBuilder speechOutputBuilder = new StringBuilder();
                speechOutputBuilder.append(speechPrefixContent);
                StringBuilder cardOutputBuilder = new StringBuilder();
                cardOutputBuilder.append(cardPrefixContent);
                for(int i=0;i<mediaNames.size();++i){
                    speechOutputBuilder.append("<p>");
                    speechOutputBuilder.append(mediaNames.get(i));
                    speechOutputBuilder.append("</p> ");
                    cardOutputBuilder.append(mediaNames.get(i));
                    cardOutputBuilder.append("\n");
                }
                speechOutputBuilder.append(" What program would you like?");
                cardOutputBuilder.append(" What program would you like?");
                speechOutput = speechOutputBuilder.toString();
                // Create the Simple card content.
                SimpleCard card = new SimpleCard();
                card.setTitle(cardTitle);
                card.setContent(cardOutputBuilder.toString());
                    
                switch(mediaType){
                    case SHOW:
                        session.setAttribute(SESSION_SHOWS, mediaNames);
                        break;
                    case BOOKMARK:
                        session.setAttribute(SESSION_BOOKMARKS, mediaNames);
                        break;
                }
                SpeechletResponse response = newAskResponse("<speak>" + speechOutput + "</speak>", true, repromptText, false);
                response.setCard(card);
                return response;
            }
        }catch(Exception ex){
            log.error("Failed to get list of shows",ex);
            return null;
        }
    }

    private SpeechletResponse handlePlayShow(Intent intent, Session session){
        try{

            String speechPrefixContent = "";
            String cardPrefixContent = "";
            String cardTitle = "Playing Show";
            String speechOutput = null;

            Slot showSlot = intent.getSlot(SLOT_SHOW);
            String showName = showSlot.getValue();
            List<String> showPaths = null;
            if(showName!=null){
                log.trace("Getting show info for {}",showName);
                URL url = new URL(MPLAYER_WS_PREFIX + "/show_info?show_name="+URLEncoder.encode(showName,"UTF-8"));
                String jsonText = getJsonString(url);
                showPaths = getJsonShowPaths(jsonText);
            }
            if(showPaths==null || showPaths.size()==0){
                speechOutput = "There were no episodes for "+showName;
                // Create the plain text output
                SsmlOutputSpeech outputSpeech = new SsmlOutputSpeech();
                outputSpeech.setSsml("<speak>" + speechOutput + "</speak>");
                return SpeechletResponse.newTellResponse(outputSpeech);
            }else{
                int totalEpisodes = showPaths.size();
                StringBuilder speechOutputBuilder = new StringBuilder();
                speechOutputBuilder.append(speechPrefixContent);
                StringBuilder cardOutputBuilder = new StringBuilder();
                cardOutputBuilder.append(cardPrefixContent);
                speechOutputBuilder.append("<p>");
                speechOutputBuilder.append("There are "+totalEpisodes+
                " to choose from.");
                speechOutputBuilder.append("</p> ");
                cardOutputBuilder.append("There are "+totalEpisodes+
                " to choose from.");
                cardOutputBuilder.append("\n");
                speechOutputBuilder.append(" What episode would you like?");
                cardOutputBuilder.append(" What episode would you like?");
                speechOutput = speechOutputBuilder.toString();
                // Create the Simple card content.
                SimpleCard card = new SimpleCard();
                card.setTitle(cardTitle);
                card.setContent(cardOutputBuilder.toString());
                    
                session.setAttribute(SESSION_SELECTED_SHOW, showName);
                session.setAttribute(SESSION_EPISODES, showPaths);
                SpeechletResponse response = newAskResponse("<speak>" + speechOutput + "</speak>", true, repromptText, false);
                response.setCard(card);
                return response;
            }
        }catch(Exception ex){
            log.error("Failed to assign list of episodes",ex);
            return null;
        }
    }

    private SpeechletResponse handlePlayEpisode(Intent intent, Session session){
        try{
            String speechPrefixContent = "";
            String cardPrefixContent = "";
            String cardTitle = "Playing Episode";
            String speechOutput = null;

            Slot episodeSlot = intent.getSlot(SLOT_EPISODE);
            String episodeName = episodeSlot.getValue();
            int episodeIndex = Integer.parseInt(episodeName) - 1;
            String showPath = null;
            String showName = (String)session.getAttribute(SESSION_SELECTED_SHOW);
            List<String> paths = (ArrayList<String>)session.getAttribute(SESSION_EPISODES);
            if(episodeIndex<paths.size()){
                showPath = paths.get(episodeIndex);
            }
            if(showPath!=null){
                log.trace("Playing episode {}",showPath);
                URL url = new URL(MPLAYER_WS_PREFIX + "/play?file="+URLEncoder.encode(showPath,"UTF-8"));
                String jsonText = getJsonString(url);
                speechOutput = "Playing episode "+episodeName+" for "+showName;
                // Create the plain text output
                SsmlOutputSpeech outputSpeech = new SsmlOutputSpeech();
                outputSpeech.setSsml("<speak>" + speechOutput + "</speak>");
                return SpeechletResponse.newTellResponse(outputSpeech);
            }else{
                speechOutput = "You selected an invalid episode "+episodeName+" for "+showName;
                // Create the plain text output
                SsmlOutputSpeech outputSpeech = new SsmlOutputSpeech();
                outputSpeech.setSsml("<speak>" + speechOutput + "</speak>");
                return SpeechletResponse.newTellResponse(outputSpeech);
            }
        }catch(Exception ex){
            log.error("Failed to play episode",ex);
            return null;
        }
    }

    private SpeechletResponse handleOpenBookmark(Intent intent, Session session){
        try{
            String speechPrefixContent = "";
            String cardPrefixContent = "";
            String cardTitle = "Opening bookmark";
            String speechOutput = null;

            Slot bookmarkSlot = intent.getSlot(SLOT_BOOKMARK);
            String bookmarkName = bookmarkSlot.getValue();
            if(bookmarkName!=null){
                log.trace("Opening bookmark {}",bookmarkName);
                URL url = new URL(BROWSER_WS_PREFIX + "/open?bookmark="+URLEncoder.encode(bookmarkName,"UTF-8"));
                String jsonText = getJsonString(url);
                speechOutput = "Opened site "+bookmarkName;
                // Create the plain text output
                SsmlOutputSpeech outputSpeech = new SsmlOutputSpeech();
                outputSpeech.setSsml("<speak>" + speechOutput + "</speak>");
                return SpeechletResponse.newTellResponse(outputSpeech);
            }else{
                speechOutput = "You did not provide a bookmark";
                // Create the plain text output
                SsmlOutputSpeech outputSpeech = new SsmlOutputSpeech();
                outputSpeech.setSsml("<speak>" + speechOutput + "</speak>");
                return SpeechletResponse.newTellResponse(outputSpeech);
            }
        }catch(Exception ex){
            log.error("Failed to open bookmark",ex);
            return null;
        }
    }


    private SpeechletResponse handleSeekSeconds(Intent intent, Session session){
        try{
            String speechPrefixContent = "";
            String cardPrefixContent = "";
            String cardTitle = "Seeking";
            String speechOutput = null;

            Slot seekSecondsSlot = intent.getSlot(SLOT_SEEK_SECONDS);
            String seekSecondsName = seekSecondsSlot.getValue();
            int seekSeconds = Integer.parseInt(seekSecondsName) - 1;
            String showName = (String)session.getAttribute(SESSION_SELECTED_SHOW);
            if(seekSeconds!=0){
                log.trace("Seeking {} seconds",seekSeconds);
                URL url = new URL(MPLAYER_WS_PREFIX + "/seek?seconds="+URLEncoder.encode(seekSecondsName,"UTF-8"));
                String jsonText = getJsonString(url);
                speechOutput = "Seeking "+seekSecondsName+" seconds for "+showName;
                // Create the plain text output
                SsmlOutputSpeech outputSpeech = new SsmlOutputSpeech();
                outputSpeech.setSsml("<speak>" + speechOutput + "</speak>");
                return SpeechletResponse.newTellResponse(outputSpeech);
            }else{
                speechOutput = "You provided "+seekSecondsName+" seconds, so no seek needed for "+showName;
                // Create the plain text output
                SsmlOutputSpeech outputSpeech = new SsmlOutputSpeech();
                outputSpeech.setSsml("<speak>" + speechOutput + "</speak>");
                return SpeechletResponse.newTellResponse(outputSpeech);
            }
        }catch(Exception ex){
            log.error("Failed to seek episode ",ex);
            return null;
        }
    }

    private SpeechletResponse handleStopIntent(Intent intent, Session session){
        try{
            URL url = new URL(MPLAYER_WS_PREFIX + "/stop");
            String jsonText = getJsonString(url);
            PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
            outputSpeech.setText("Goodbye");
            return SpeechletResponse.newTellResponse(outputSpeech);
        }catch(Exception ex){
            log.error("Failed to seek episode ",ex);
            return null;
        }
    }

    private String getJsonString(URL url){
        InputStreamReader inputStream = null;
        BufferedReader bufferedReader = null;
        String text = null;
        try {
            String line;
            //String urlStr = MPLAYER_WS_PREFIX + "/list";
            //log.info("Creating a URL {}",urlStr);
            //URL url = new URL(urlStr);
            inputStream = new InputStreamReader(url.openStream(), Charset.forName("UTF-8"));
            bufferedReader = new BufferedReader(inputStream);
            StringBuilder builder = new StringBuilder();
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line);
            }
            text = builder.toString();
        } catch (IOException e) {
            // reset text variable to a blank string
            text = "";
        } finally {
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(bufferedReader);
        }
        return text;
    }

    private List<String> getJsonShowPaths(String jsonText){
        if(jsonText==null) return null;
        try{
            JSONObject jsonObject = new JSONObject(jsonText);
            JSONObject responseObject = jsonObject.getJSONObject("response");
            JSONArray episodesArray = responseObject.getJSONArray("files");
            List<String> episodes = new ArrayList<String>();
            for(int i=0;i<episodesArray.length();++i){
                episodes.add(episodesArray.getString(i));
            }
            return episodes;
        }catch(Exception ex){
            log.error("Problem parsing JSON",ex);
        }
        return null;
    }

    private List<String> getJsonMediaNames(String jsonText,MediaType mediaType){
        if(jsonText==null) return null;
        try{
            JSONObject jsonObject = new JSONObject(jsonText);
            JSONObject responseObject = jsonObject.getJSONObject("response");
            JSONArray mediaArray = null;
            switch(mediaType){
                case SHOW:
                    mediaArray = responseObject.getJSONArray("shows");
                    break;
                case BOOKMARK:
                    mediaArray = responseObject.getJSONArray("bookmarks");
                    break;
            }
            List<String> media = new ArrayList<String>();
            for(int i=0;i<mediaArray.length();++i){
                media.add(mediaArray.getString(i));
            }
            return media;
        }catch(Exception ex){
            log.error("Problem parsing JSON",ex);
        }
        return null;
    }

    /**
     * Wrapper for creating the Ask response from the input strings.
     * 
     * @param stringOutput
     *            the output to be spoken
     * @param isOutputSsml
     *            whether the output text is of type SSML
     * @param repromptText
     *            the reprompt for if the user doesn't reply or is misunderstood.
     * @param isRepromptSsml
     *            whether the reprompt text is of type SSML
     * @return SpeechletResponse the speechlet response
     */
    private SpeechletResponse newAskResponse(String stringOutput, boolean isOutputSsml, String repromptText, boolean isRepromptSsml) {
        OutputSpeech outputSpeech, repromptOutputSpeech;
        if (isOutputSsml) {
            outputSpeech = new SsmlOutputSpeech();
            ((SsmlOutputSpeech) outputSpeech).setSsml(stringOutput);
        } else {
            outputSpeech = new PlainTextOutputSpeech();
            ((PlainTextOutputSpeech) outputSpeech).setText(stringOutput);
        }

        if (isRepromptSsml) {
            repromptOutputSpeech = new SsmlOutputSpeech();
            ((SsmlOutputSpeech) repromptOutputSpeech).setSsml(repromptText);
        } else {
            repromptOutputSpeech = new PlainTextOutputSpeech();
            ((PlainTextOutputSpeech) repromptOutputSpeech).setText(repromptText);
        }
        Reprompt reprompt = new Reprompt();
        reprompt.setOutputSpeech(repromptOutputSpeech);
        return SpeechletResponse.newAskResponse(outputSpeech, reprompt);
    }

}
