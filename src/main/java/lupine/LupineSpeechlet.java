/** Copyright 2014-2015 Amazon.com, Inc. or its affiliates. All Rights Reserved.

    Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License. A copy of the License is located at

        http://aws.amazon.com/apache2.0/

    or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */
package lupine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.json.JSONArray;
import org.json.JSONObject;

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
 * <li><b>Web service</b>: communicate with an external web service to get events for specified days
 * in history (Wikipedia API)</li>
 * <li><b>Pagination</b>: after obtaining a list of events, read a small subset of events and wait
 * for user prompt to read the next subset of events by maintaining session state</li>
 * <p>
 * <li><b>Dialog and Session state</b>: Handles two models, both a one-shot ask and tell model, and
 * a multi-turn dialog model</li>
 * <li><b>SSML</b>: Using SSML tags to control how Alexa renders the text-to-speech</li>
 * </ul>
 * <p>
 * <h2>Examples</h2>
 * <p>
 * <b>One-shot model</b>
 * <p>
 * User: "Alexa, ask History Buff what happened on August thirtieth."
 * <p>
 * Alexa: "For August thirtieth, in 2003, [...] . Wanna go deeper in history?"
 * <p>
 * User: "No."
 * <p>
 * Alexa: "Good bye!"
 * <p>
 * 
 * <b>Dialog model</b>
 * <p>
 * User: "Alexa, open History Buff"
 * <p>
 * Alexa: "History Buff. What day do you want events for?"
 * <p>
 * User: "August thirtieth."
 * <p>
 * Alexa: "For August thirtieth, in 2003, [...] . Wanna go deeper in history?"
 * <p>
 * User: "Yes."
 * <p>
 * Alexa: "In 1995, Bosnian war [...] . Wanna go deeper in history?"
 * <p>
 * User: "No."
 * <p>
 * Alexa: "Good bye!"
 * <p>
 */
public class LupineSpeechlet implements Speechlet {
    private static final Logger log = LoggerFactory.getLogger(LupineSpeechlet.class);

    /**
     * URL prefix to download history content from Wikipedia.
     */
    private static final String URL_PREFIX =
    "http://www.caseyandgary.com:8000/mplayer/";
//       "https://en.wikipedia.org/w/api.php?action=query&prop=extracts"
//     + "&format=json&explaintext=&exsectionformat=plain&redirects=&titles=";

    /**
     * Constant defining number of events to be read at one time.
     */
    private static final int PAGINATION_SIZE = 3;

    /**
     * Length of the delimiter between individual events.
     */
    private static final int DELIMITER_SIZE = 2;

    /**
     * Constant defining session attribute key for the event index.
     */
    private static final String SESSION_INDEX = "index";

    /**
     * Constant defining session attribute key for the event text key for date of events.
     */
    private static final String SESSION_TEXT = "text";

    private static final String helpText = "With the video manager, you can ask for available shows, ask for available episodes, and play a particular episode. For example, you could say what shows are available, or what episodes are available, or play episode 4. So, what would you like to ask?";
    private static final String repromptText = "What would you like to ask?";

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

        if ("ListShowsIntent".equals(intentName)) {
            return handleListShows(intent,session);
        }else if ("ListEpisodesIntent".equals(intentName)) {
            return handleListEpisodes(intent,session);
        }else if ("PlayEpisodeIntent".equals(intentName)) {
            return handlePlayEpisode(intent,session);
        } else if ("AMAZON.HelpIntent".equals(intentName)) {
            // Create the plain text output.
            return newAskResponse(helpText, false, repromptText, false);
        } else if ("AMAZON.StopIntent".equals(intentName)) {
            PlainTextOutputSpeech outputSpeech = new PlainTextOutputSpeech();
            outputSpeech.setText("Goodbye");

            return SpeechletResponse.newTellResponse(outputSpeech);
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

    /**
     * Prepares the speech to reply to the user. Obtain events from Wikipedia for the date specified
     * by the user (or for today's date, if no date is specified), and return those events in both
     * speech and SimpleCard format.
     * 
     * @param intent
     *            the intent object which contains the date slot
     * @param session
     *            the session object
     * @return SpeechletResponse object with voice/card response to return to the user
     */
    private SpeechletResponse handleFirstEventRequest(Intent intent, Session session) {
        Calendar calendar = null;
        //Calendar calendar = getCalendar(intent);
        String month = null;
        //String month = MONTH_NAMES[calendar.get(Calendar.MONTH)];
        String date = Integer.toString(calendar.get(Calendar.DATE));

        String speechPrefixContent = "<p>For " + month + " " + date + "</p> ";
        String cardPrefixContent = "For " + month + " " + date + ", ";
        String cardTitle = "Events on " + month + " " + date;

        ArrayList<String> events = null;
        //ArrayList<String> events = getJsonEventsFromWikipedia(month, date);
        String speechOutput = "";
        if (events.isEmpty()) {
            speechOutput =
                    "There is a problem connecting to Wikipedia at this time."
                            + " Please try again later.";

            // Create the plain text output
            SsmlOutputSpeech outputSpeech = new SsmlOutputSpeech();
            outputSpeech.setSsml("<speak>" + speechOutput + "</speak>");

            return SpeechletResponse.newTellResponse(outputSpeech);
        } else {
            StringBuilder speechOutputBuilder = new StringBuilder();
            speechOutputBuilder.append(speechPrefixContent);
            StringBuilder cardOutputBuilder = new StringBuilder();
            cardOutputBuilder.append(cardPrefixContent);
            for (int i = 0; i < PAGINATION_SIZE; i++) {
                speechOutputBuilder.append("<p>");
                speechOutputBuilder.append(events.get(i));
                speechOutputBuilder.append("</p> ");
                cardOutputBuilder.append(events.get(i));
                cardOutputBuilder.append("\n");
            }
            speechOutputBuilder.append(" Wanna go deeper in history?");
            cardOutputBuilder.append(" Wanna go deeper in history?");
            speechOutput = speechOutputBuilder.toString();

            String repromptText =
                    "With History Buff, you can get historical events for any day of the year."
                            + " For example, you could say today, or August thirtieth."
                            + " Now, which day do you want?";

            // Create the Simple card content.
            SimpleCard card = new SimpleCard();
            card.setTitle(cardTitle);
            card.setContent(cardOutputBuilder.toString());

            // After reading the first 3 events, set the count to 3 and add the events
            // to the session attributes
            session.setAttribute(SESSION_INDEX, PAGINATION_SIZE);
            session.setAttribute(SESSION_TEXT, events);

            SpeechletResponse response = newAskResponse("<speak>" + speechOutput + "</speak>", true, repromptText, false);
            response.setCard(card);
            return response;
        }
    }

    private SpeechletResponse handleListShows(Intent intent, Session session){
        try{

            String speechPrefixContent = "<p>The list of shows are</p> ";
            String cardPrefixContent = "The list of shows are: ";
            String cardTitle = "Shows";
            String speechOutput = null;

            URL url = new URL(URL_PREFIX + "/list");
            String jsonText = getJsonString(url);
            List<String> showNames = getJsonShowNames(jsonText);
            if(showNames==null){
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
                for(int i=0;i<showNames.size();++i){
                    speechOutputBuilder.append("<p>");
                    speechOutputBuilder.append(showNames.get(i));
                    speechOutputBuilder.append("</p> ");
                    cardOutputBuilder.append(showNames.get(i));
                    cardOutputBuilder.append("\n");
                }
                speechOutputBuilder.append(" What next?");
                cardOutputBuilder.append(" What next?");
                speechOutput = speechOutputBuilder.toString();
                // Create the Simple card content.
                SimpleCard card = new SimpleCard();
                card.setTitle(cardTitle);
                card.setContent(cardOutputBuilder.toString());
                    
                session.setAttribute(SESSION_TEXT, showNames);
                SpeechletResponse response = newAskResponse("<speak>" + speechOutput + "</speak>", true, repromptText, false);
                response.setCard(card);
                return response;
            }
        }catch(Exception ex){
            log.error("Failed to get list of shows",ex);
            return null;
        }
    }

    private SpeechletResponse handleListEpisodes(Intent intent, Session session){
        return null;
    }

    private SpeechletResponse handlePlayEpisode(Intent intent, Session session){
        return null;
    }
    /**
     * Prepares the speech to reply to the user. Obtains the list of events as well as the current
     * index from the session attributes. After getting the next set of events, increment the index
     * and store it back in session attributes. This allows us to obtain new events without making
     * repeated network calls, by storing values (events, index) during the interaction with the
     * user.
     * 
     * @param session
     *            object containing session attributes with events list and index
     * @return SpeechletResponse object with voice/card response to return to the user
     */
    private SpeechletResponse handleNextEventRequest(Session session) {
        String cardTitle = "More events on this day in history";
        ArrayList<String> events = (ArrayList<String>) session.getAttribute(SESSION_TEXT);
        int index = (Integer) session.getAttribute(SESSION_INDEX);
        String speechOutput = "";
        String cardOutput = "";
        if (events == null) {
            speechOutput =
                    "With History Buff, you can get historical events for any day of the year."
                            + " For example, you could say today, or August thirtieth."
                            + " Now, which day do you want?";
        } else if (index >= events.size()) {
            speechOutput =
                    "There are no more events for this date. Try another date by saying, "
                            + " get events for august thirtieth.";
        } else {
            StringBuilder speechOutputBuilder = new StringBuilder();
            StringBuilder cardOutputBuilder = new StringBuilder();
            for (int i = 0; i < PAGINATION_SIZE && index < events.size(); i++) {
                speechOutputBuilder.append("<p>");
                speechOutputBuilder.append(events.get(index));
                speechOutputBuilder.append("</p> ");
                cardOutputBuilder.append(events.get(index));
                cardOutputBuilder.append(" ");
                index++;
            }
            if (index < events.size()) {
                speechOutputBuilder.append(" Wanna go deeper in history?");
                cardOutputBuilder.append(" Wanna go deeper in history?");
            }
            session.setAttribute(SESSION_INDEX, index);
            speechOutput = speechOutputBuilder.toString();
            cardOutput = cardOutputBuilder.toString();
        }
        String repromptText = "Do you want to know more about what happened on this date?";

        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle(cardTitle);
        card.setContent(cardOutput.toString());

        SpeechletResponse response = newAskResponse("<speak>" + speechOutput + "</speak>", true, repromptText, false);
        response.setCard(card);
        return response;
    }

    private String getJsonString(URL url){
        InputStreamReader inputStream = null;
        BufferedReader bufferedReader = null;
        String text = null;
        try {
            String line;
            //String urlStr = URL_PREFIX + "/list";
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

    private List<String> getJsonShowNames(String jsonText){
        if(jsonText==null) return null;
        try{
            JSONObject jsonObject = new JSONObject(jsonText);
            JSONObject responseObject = jsonObject.getJSONObject("response");
            JSONArray showsArray = responseObject.getJSONArray("shows");
            List<String> shows = new ArrayList<String>();
            for(int i=0;i<showsArray.length();++i){
                shows.add(showsArray.getString(i));
            }
            return shows;
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
    private SpeechletResponse newAskResponse(String stringOutput, boolean isOutputSsml,
            String repromptText, boolean isRepromptSsml) {
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
