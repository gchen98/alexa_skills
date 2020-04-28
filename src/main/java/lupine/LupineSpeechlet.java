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
 * This sample movies how to create a Lambda function for handling Alexa Skill requests that:
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
 * User: "Alexa, ask Movie Player what channels are available"
 * <p>
 * Alexa: "The list of channels are KCBS,KNBC,KTLA,KABC,KCAL,KTTV,PBS-1,PBS-2,PBS-3,PBS-4,KLCS-1,KLCS-2. Which channel would you like?"
 * <p>
 * User: "Alexa, turn off movie player."
 * <p>
 * User: "Alexa, ask Movie Player what movies are available"
 * <p>
 * Alexa: "The list of movies are Simpsons, Big Bang Theory, Last Man On Earth; What movie would you like?"
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

    private final String INTENT_REFRESH_MOVIES= "RefreshMoviesIntent";
    private final String INTENT_LIST_MOVIES= "ListMoviesIntent";
    private final String INTENT_LIST_CHANNELS= "ListChannelsIntent";
    private final String INTENT_LIST_BOOKMARKS = "ListBookmarksIntent";
    private final String INTENT_PLAY_CHANNEL = "PlayChannelIntent";
    private final String INTENT_PLAY_MOVIE = "PlayMovieIntent";
    private final String INTENT_PLAY_EPISODE = "PlayEpisodeIntent";
    private final String INTENT_OPEN_BOOKMARK = "OpenBookmarkIntent";
    private final String INTENT_SEEK_SECONDS = "SeekSecondsIntent";
    private final String INTENT_STOP = "AMAZON.StopIntent";
    private final String INTENT_HELP = "AMAZON.HelpIntent";

    private static final Logger log = LoggerFactory.getLogger(
    LupineSpeechlet.class);
    private final String MEDIA_TYPE_CHANNELS = "channels";
    private final String MEDIA_TYPE_MOVIES = "movies";


    private static final String MPLAYER_WS_PREFIX =
    "http://www.caseyandgary.com:9000/mplayer/";

    private static final String BROWSER_WS_PREFIX =
    "http://www.caseyandgary.com:9000/browser/";

    private static final String SLOT_BOOKMARK = "bookmark";
    private static final String SLOT_MOVIE = "movie";
    private static final String SLOT_CHANNEL = "channel";
    private static final String SLOT_EPISODE = "episode";
    private static final String SLOT_SEEK_SECONDS = "seek_seconds";

    //private static final String SESSION_MOVIES = "movies";
    //private static final String SESSION_CHANNELS = "channels";
    //private static final String SESSION_BOOKMARKS = "bookmarks";
    private static final String SESSION_SELECTED_MOVIE = "selected_movie";
    private static final String SESSION_EPISODES = "episodes";

    private static final String helpText = "With our kitchen TV Alexa app, "+
    "you can ask to play movies or TV channels. "+
    "Examples: What movies are available? or what channels are available?"+
    " To watch a movie say, play movie moviename.  To watch a TV channel say, "+
    "play channel channel number.  To reload the movie channel list on the "+
    "server, say refresh movie list." ;

    enum MediaType{
        MOVIE,CHANNEL,BOOKMARK
    }

    @Override
    public void onSessionStarted(final SessionStartedRequest request, 
    final Session session) throws SpeechletException {
        log.info("onSessionStarted requestId={}, sessionId={}", 
        request.getRequestId(), session.getSessionId());
        // any initialization logic goes here
    }

    @Override
    public SpeechletResponse onLaunch(final LaunchRequest request, 
    final Session session) throws SpeechletException {
        log.info("onLaunch requestId={}, sessionId={}", 
        request.getRequestId(), session.getSessionId());

        return newTellResponse("<speak>Kitchen TV app. Say Alexa help for more info.</speak>", true,false);

        //return newAskResponse(helpText, false, 
        //"What would you like to do?", false);
    }

    @Override
    public SpeechletResponse onIntent(final IntentRequest request, 
    final Session session) throws SpeechletException {
        log.info("onIntent requestId={}, sessionId={}", 
        request.getRequestId(), session.getSessionId());
        Intent intent = request.getIntent();
        String intentName = intent.getName();

        if (INTENT_LIST_MOVIES.equals(intentName)
        || INTENT_LIST_CHANNELS.equals(intentName)
        || INTENT_LIST_BOOKMARKS.equals(intentName)
        ) {
            MediaType mediaType = null;
            if(INTENT_LIST_MOVIES.equals(intentName)){
                mediaType = MediaType.MOVIE;
            }else if(INTENT_LIST_CHANNELS.equals(intentName)){
                mediaType = MediaType.CHANNEL;
            }else if(INTENT_LIST_BOOKMARKS.equals(intentName)){
                mediaType = MediaType.BOOKMARK;
            }
            return handleListMedia(intent,session,mediaType);
        }else if (INTENT_PLAY_MOVIE.equals(intentName)) {
        //    return handlePlayMovie(intent,session);
            return handlePlayMedia(intent,session,MediaType.MOVIE);
        //}else if (INTENT_PLAY_EPISODE.equals(intentName)) {
        //    return handlePlayEpisode(intent,session);
        }else if (INTENT_PLAY_CHANNEL.equals(intentName)){
            return handlePlayMedia(intent,session,MediaType.CHANNEL);
        }else if (INTENT_OPEN_BOOKMARK.equals(intentName)) {
            return handleOpenBookmark(intent,session);
        }else if (INTENT_SEEK_SECONDS.equals(intentName)) {
            return handleSeekSeconds(intent,session);
        }else if (INTENT_REFRESH_MOVIES.equals(intentName)) {
            return handleRefreshMovies(intent,session);
        } else if (INTENT_HELP.equals(intentName)) {
            // Create the plain text output.
            return newAskResponse(helpText, false, 
            "What would you like to do?", false);
        } else if (INTENT_STOP.equals(intentName)) {
            return handleStopIntent(intent,session);
        } else {
            throw new SpeechletException("Invalid Intent");
        }
    }

    @Override
    public void onSessionEnded(final SessionEndedRequest request, 
    final Session session) throws SpeechletException {
        log.info("onSessionEnded requestId={}, sessionId={}", 
        request.getRequestId(), session.getSessionId());

        // any session cleanup logic would go here
    }

    private SpeechletResponse handleListMedia(Intent intent, 
    Session session,MediaType mediaType){
        try{
            String cardPrefixContent = "Here is your list of "+mediaType;
            String speechPrefixContent = "<p>"+cardPrefixContent+"</p> ";
            String cardTitle = "Media names";
            String speechOutput = null;
            URL url = null;
            switch(mediaType){
                case MOVIE:
                    url = new URL(MPLAYER_WS_PREFIX + "/list?type=movies");
                    break;
                case CHANNEL:
                    url = new URL(MPLAYER_WS_PREFIX + "/list?type=channels");
                    break;
                case BOOKMARK:
                    url = new URL(BROWSER_WS_PREFIX + "/list?type=bookmarks");
                    break;
            }
            log.debug("Got url string of {}",url);
            String jsonText = getJsonString(url);
            List<String> mediaNames = getJsonMediaNames(jsonText,mediaType);
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
            String repromptText = "What program would you like?";
            speechOutputBuilder.append(repromptText);
            cardOutputBuilder.append(repromptText);
            speechOutput = speechOutputBuilder.toString();
            // Create the Simple card content.
            SimpleCard card = new SimpleCard();
            card.setTitle(cardTitle);
            card.setContent(cardOutputBuilder.toString());
            //switch(mediaType){
            //    case MOVIE:
            //        session.setAttribute(SESSION_MOVIES, mediaNames);
            //        break;
            //    case BOOKMARK:
            //        session.setAttribute(SESSION_BOOKMARKS, mediaNames);
            //        break;
            //    case CHANNEL:
            //        session.setAttribute(SESSION_CHANNELS, mediaNames);
            //        break;
            // }
            SpeechletResponse response = newAskResponse("<speak>" + 
            speechOutput + "</speak>", true, repromptText, false);
            response.setCard(card);
            return response;
        } catch(Exception ex){
            log.error("Failed to get list of movies",ex);
            return newTellResponse(
            "<speak>Failed to get list of movies because of error: " + 
            ex.getMessage() + "</speak>",true,false);
        }
    }

    private SpeechletResponse handlePlayMovie(Intent intent, Session session){
        try{
            String speechPrefixContent = "";
            String cardPrefixContent = "";
            String cardTitle = "Playing Movie";
            String speechOutput = null;

            Slot movieSlot = intent.getSlot(SLOT_MOVIE);
            String movieName = movieSlot.getValue();
            log.debug("Got movie name of {}",movieName);
            List<String> moviePaths = null;
            if(movieName!=null){
                log.debug("Getting movie info for {}",movieName);
                URL url = new URL(MPLAYER_WS_PREFIX + 
                "/movie_info?movie_name="+URLEncoder.encode(movieName,"UTF-8"));
                String jsonText = getJsonString(url);
                moviePaths = getJsonMoviePaths(jsonText);
            }
            if(moviePaths==null || moviePaths.size()==0){
                speechOutput = "There were no episodes for "+movieName;
                // Create the plain text output
                return newTellResponse("<speak>" + speechOutput + 
                "</speak>",true,false);
            }else{
                int totalEpisodes = moviePaths.size();
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
                String repromptText = "What episode would you like?";
                speechOutputBuilder.append(repromptText);
                cardOutputBuilder.append(repromptText);
                speechOutput = speechOutputBuilder.toString();
                // Create the Simple card content.
                SimpleCard card = new SimpleCard();
                card.setTitle(cardTitle);
                card.setContent(cardOutputBuilder.toString());
                    
                session.setAttribute(SESSION_SELECTED_MOVIE, movieName);
                session.setAttribute(SESSION_EPISODES, moviePaths);
                SpeechletResponse response = newAskResponse("<speak>" + 
                speechOutput + "</speak>", true, repromptText, false);
                response.setCard(card);
                return response;
            }
        }catch(Exception ex){
            log.error("Failed to assign list of episodes",ex);
            return newTellResponse(
            "<speak>Failed to assign list of episodes because of error: " + 
            ex.getMessage() + "</speak>",true,false);
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
            String mediaPath = null;
            String mediaName = (String)session.
            getAttribute(SESSION_SELECTED_MOVIE);
            List<String> paths = (ArrayList<String>)session.
            getAttribute(SESSION_EPISODES);
            if(episodeIndex<paths.size()){
                mediaPath = paths.get(episodeIndex);
            }
            if(mediaPath!=null){
                log.debug("Playing episode {}",mediaPath);
                URL url = new URL(MPLAYER_WS_PREFIX + 
                "/play?type="+MEDIA_TYPE_MOVIES+"&file="+
                URLEncoder.encode(mediaPath,"UTF-8"));
                String jsonText = getJsonString(url);
                speechOutput = "Playing episode "+episodeName+" for "+mediaName+
                ". You may now issue commands like Skip 20 seconds, or stop.";
                return newTellResponse("<speak>" + speechOutput + "</speak>",
                true,false);
            }else{
                speechOutput = "You selected an invalid episode "+
                episodeName+" for "+mediaName;
                // Create the plain text output
                SsmlOutputSpeech outputSpeech = new SsmlOutputSpeech();
                outputSpeech.setSsml("<speak>" + speechOutput + "</speak>");

                // Create the Simple card content.
                SimpleCard card = new SimpleCard();
                card.setTitle(cardTitle);
                card.setContent("Playing episode.");
                String repromptText = "What episode would you like?";
                SpeechletResponse response = newAskResponse("<speak>" + 
                speechOutput + "</speak>", true, repromptText, false);
                response.setCard(card);
                return response;
            }
        }catch(Exception ex){
            log.error("Failed to play episode",ex);
            return newTellResponse(
            "<speak>Failed to play episode because of error: " + 
            ex.getMessage() + "</speak>",true,false);
        }
    }

    private SpeechletResponse handlePlayMedia(Intent intent, 
    Session session,MediaType mediaType){
        try{
            String mediaTypeStr = null;
            String mediaName = null;
            if (mediaType==MediaType.MOVIE){
                mediaTypeStr = MEDIA_TYPE_MOVIES;
                Slot slot = intent.getSlot(SLOT_MOVIE);
                mediaName = slot.getValue();
            }else if (mediaType==MediaType.CHANNEL){
                mediaTypeStr = MEDIA_TYPE_CHANNELS;
                Slot slot = intent.getSlot(SLOT_CHANNEL);
                mediaName = slot.getValue();
            }
            String cardTitle = "Playing media type "+mediaTypeStr;
            String speechPrefixContent = "";
            String cardPrefixContent = "";
            String speechOutput = null;
            log.debug("Playing media {}",mediaName);
            session.setAttribute(SESSION_SELECTED_MOVIE, mediaName);
            URL url = new URL(MPLAYER_WS_PREFIX + "/play?type="+
            mediaTypeStr+"&file="+URLEncoder.encode(mediaName,"UTF-8"));
            String jsonText = getJsonString(url);
            speechOutput = "Playing "+mediaName;
            return newTellResponse("<speak>" + speechOutput + "</speak>",
            true,false);
        }catch(Exception ex){
            log.error("Failed to play channel",ex);
            return newTellResponse(
            "<speak>Failed to play channel. Error was: " + ex.getMessage() + "</speak>",true,false);
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
            String mediaName = (String)session.getAttribute(
            SESSION_SELECTED_MOVIE);
            if(seekSeconds!=0){
                log.debug("Seeking {} seconds",seekSeconds);
                URL url = new URL(MPLAYER_WS_PREFIX + 
                "/seek?seconds="+URLEncoder.encode(seekSecondsName,"UTF-8"));
                String jsonText = getJsonString(url);
                speechOutput = "Seeking "+seekSecondsName+" seconds."; 
                return newTellResponse("<speak>" + speechOutput + "</speak>",
                true,false);
            }else{
                speechOutput = "You provided "+seekSecondsName+
                " seconds, so no seek needed for "+mediaName;
                return newTellResponse("<speak>" + speechOutput + "</speak>",
                true,false);
            }
        }catch(Exception ex){
            log.error("Failed to seek episode ",ex);
            return newTellResponse(
            "<speak>Failed to seek episode because of error: " +
            ex.getMessage()  + "</speak>", true,false);
        }
    }

    private SpeechletResponse handleRefreshMovies(Intent intent, 
    Session session){
        try{
            String speechPrefixContent = "";
            String cardPrefixContent = "";
            String cardTitle = "Seeking";
            String speechOutput = null;

            log.debug("Refreshing movie list");
            URL url = new URL(MPLAYER_WS_PREFIX + "/refresh");
            String jsonText = getJsonString(url);
            speechOutput = "Refreshing movie and channel list";
            return newTellResponse("<speak>" + speechOutput + 
            "</speak>",true,false);
        }catch(Exception ex){
            log.error("Failed to refresh movie list ",ex);
            String speechOutput = 
            "In refreshing list, an error message occured of "+
            ex.getMessage();
            return newTellResponse("<speak>" + 
            speechOutput + "</speak>",true,false);
        }
    }

    private SpeechletResponse handleStopIntent(Intent intent, Session session){
        try{
            URL url = new URL(MPLAYER_WS_PREFIX + "/stop");
            log.debug("Asked mplayer to stop");
            String jsonText = getJsonString(url);
            return newTellResponse("Goodbye",false,true);
        }catch(Exception ex){
            log.error("Failed to seek episode ",ex);
            return newTellResponse("An error occured in goodbye: "+
            ex.getMessage(),false,true);
        }
    }

    private SpeechletResponse handleOpenBookmark(
    Intent intent, Session session){
        try{
            String speechPrefixContent = "";
            String cardPrefixContent = "";
            String cardTitle = "Opening bookmark";
            String speechOutput = null;

            Slot bookmarkSlot = intent.getSlot(SLOT_BOOKMARK);
            String bookmarkName = bookmarkSlot.getValue();
            if(bookmarkName!=null){
                log.debug("Opening bookmark {}",bookmarkName);
                URL url = new URL(BROWSER_WS_PREFIX + 
                "/open?bookmark="+URLEncoder.encode(bookmarkName,"UTF-8"));
                String jsonText = getJsonString(url);
                speechOutput = "Opened site "+bookmarkName;
                return newTellResponse("<speak>" + speechOutput + "</speak>",
                true,false);
            }else{
                speechOutput = "You did not provide a bookmark";
                // Create the plain text output
                return newTellResponse("<speak>" + speechOutput + "</speak>",
                true,false);
            }
        }catch(Exception ex){
            log.error("Failed to open bookmark",ex);
            return newTellResponse("<speak> Problem opening bookmark: "+
            ex.getMessage()+ "</speak>",true,false);
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

    private List<String> getJsonMoviePaths(String jsonText){
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

    private List<String> getJsonMediaNames(String jsonText,MediaType mediaType)
    throws Exception{
        if(jsonText==null) return null;
        JSONObject jsonObject = new JSONObject(jsonText);
        JSONObject responseObject = jsonObject.getJSONObject("response");
        JSONArray mediaArray = null;
        switch(mediaType){
            case MOVIE:
                mediaArray = responseObject.getJSONArray("movies");
                break;
            case BOOKMARK:
                mediaArray = responseObject.getJSONArray("bookmarks");
                break;
            case CHANNEL:
                mediaArray = responseObject.getJSONArray("channels");
                break;
        }
        List<String> media = new ArrayList<String>();
        for(int i=0;i<mediaArray.length();++i){
            media.add(mediaArray.getString(i));
        }
        return media;
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

    /**
     * Wrapper for creating the Tell response from the input strings.
     *
     * @param stringOutput
     *            the output to be spoken
     * @param isOutputSsml whether the output text is of type SSML
     * @param isEndSession whether to end the session after the tell response
     *
     * @return SpeechletResponse the speechlet response
     */
    private SpeechletResponse newTellResponse(String stringOutput, boolean isOutputSsml, boolean isEndSession) {
        OutputSpeech outputSpeech;
        if (isOutputSsml) {
            outputSpeech = new SsmlOutputSpeech();
            ((SsmlOutputSpeech) outputSpeech).setSsml(stringOutput);
        } else {
            outputSpeech = new PlainTextOutputSpeech();
            ((PlainTextOutputSpeech) outputSpeech).setText(stringOutput);
        }
        SpeechletResponse speechletResponse = SpeechletResponse.newTellResponse(outputSpeech);
        speechletResponse.setShouldEndSession(isEndSession);
        return speechletResponse;
    }

}
