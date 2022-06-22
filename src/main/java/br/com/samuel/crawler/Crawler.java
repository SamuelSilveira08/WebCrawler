package br.com.samuel.crawler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Crawler {
	
	// Declaring and initializing some important static variables

	private static volatile int currentPritingQueueNumber = 1;
	private static int thisThreadPositionInPrintingQueue = 1;
	private static int currentRankingPosition = 10;

	/**
	 * 
	 * Main method of the application. Here I create a Thread for each for loop.
	 * They will be responsible for getting the movies data asynchronously. Lambda
	 * expression was used in this method to implement the Runnable's interface
	 * {@code Runnable#run()} method. Inside it, after the crawl is made in the
	 * given url's page, the method {@code Crawler#showRanking(Map, int)} is called
	 * with the results and a integer parameter, which makes the application print
	 * the results in the order I want (from best note to worst). Without it, the
	 * threads are just gonna print the results as soon as they are ready and it
	 * will be out of order. Is also important to explain that, inside the
	 * {@code Runnable#run()} implementation, the reviewsUrl variable is adding the
	 * filters to the reviews url so the crawler can get the best reviews data
	 * easier.
	 * 
	 * @author Samuel Silveira
	 */

	public static void main(String[] args) {
		List<String> movieUrls = gatherBottomMoviesUrls();

		for (String url : movieUrls) {
			final int tempThisThreadPositionInQueue = thisThreadPositionInPrintingQueue;
			final int tempCurrentRankingPosition = currentRankingPosition;
			Runnable task = () -> {
				String reviewsUrl = url + "reviews?sort=userRating&dir=desc&ratingFilter=0";
				Map<String, String> results = getMovieData(url, reviewsUrl, tempCurrentRankingPosition);
				showRanking(results, tempThisThreadPositionInQueue);
			};
			currentRankingPosition -= 1;
			Thread thread = new Thread(task);
			thread.start();
			thisThreadPositionInPrintingQueue++;
		}
	}

	/**
	 * Method responsible for printing the results in the right order. The while
	 * inside it just keeps checking whether this thread's results turn to be print
	 * has come. When the results are printed, the current thread adds 1 to the
	 * {@code Crawler#currentQueueNumber} variable so the others threads can print
	 * their results as well.
	 * 
	 * @param results
	 * @param positionInQueue
	 * 
	 * @author Samuel Silveira
	 */

	private static void showRanking(Map<String, String> results, int positionInQueue) {
		while (positionInQueue > currentPritingQueueNumber) {
			continue;
		}
		results.forEach((key, value) -> System.out.println(key + ": " + value));
		System.out.println("************");
		currentPritingQueueNumber += 1;
	}

	/**
	 * The method {@code Crawler#gatherBottomMoviesUrls()} the top 10 worst movies'
	 * url and format it, removing some query parameters from the url so the
	 * application can add the query parameters to filter the reviews easier.
	 * 
	 * Also, before returning the list of urls, the code below reverses its order,
	 * so it can get the movies from the best note to the worst.
	 * 
	 * @return a list with the 10 worst movies urls
	 * 
	 * @author Samuel Silveira
	 */

	private static List<String> gatherBottomMoviesUrls() {
		List<String> moviesUrls = new ArrayList<>();

		Connection connection = Jsoup.connect("https://www.imdb.com/chart/bottom").header("accept-language",
				"en-US,en;q=0.9");
		try {
			Document doc = connection.get();
			Elements urls = doc.getElementsByClass("titleColumn").select("a[href]");
			for (int i = 0; i < 10; i++) {
				String url = urls.get(i).absUrl("href");
				url = url.replace(url.substring(url.indexOf("?")), "");
				moviesUrls.add(url);
			}
			Collections.reverse(moviesUrls);
			return moviesUrls;

		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * The {@code Crawler#getMovieData(String, String, int)} method is responsible
	 * for retrieving all data wanted from the movies given. It connects to the urls
	 * needed and using css classes, html tags etc. it retrieves the movies' data.
	 * 
	 * For this program, I've used the rating of the 10 worst movies list page, as
	 * they were different from the movie page itself. To do this, the method
	 * receives a integer parameter representing the rankingPosition of the movie
	 * that's been crawled so the code can know what of the "strong" tags (that
	 * holds the rating of the movies) it is going to get. It works because JSoup
	 * orders the list of Elements accordingly to the order they are found in the
	 * html file.
	 * 
	 * @param url
	 * @param reviewsUrl
	 * @param rankingPosition
	 * @return a map with key and value of the movies' data, like (Rating: 2.1, Title: Disaster Movie).
	 * 
	 * @author Samuel Silveira
	 */

	private static Map<String, String> getMovieData(String url, String reviewsUrl, int rankingPosition) {

		Map<String, String> movieData = new LinkedHashMap<>();

		List<String> directorsList = new ArrayList<>();
		List<String> castList = new ArrayList<>();
		List<String> reviews = new ArrayList<>();

		Connection connection = Jsoup.connect(url).header("accept-language", "en-US,en;q=0.9");
		Connection connectionWithReviews = Jsoup.connect(reviewsUrl);
		Connection rankPageConnection = Jsoup.connect("https://www.imdb.com/chart/bottom");

		try {
			Document doc = connection.get();
			Document docRankPage = rankPageConnection.get();
			Elements elements = doc.getElementsByClass("sc-fa02f843-0");

			for (Element element : elements.get(0).getElementsByClass("ipc-metadata-list__item")) {
				if (element.select("span").text().startsWith("Director")) {
					Elements directors = element.getElementsByClass("ipc-metadata-list-item__list-content-item");
					directors.forEach(director -> directorsList.add(director.text()));
				}
				if (element.select("a[href]").first().text().startsWith("Star")) {
					Elements cast = element.getElementsByClass("ipc-metadata-list-item__list-content-item");
					cast.forEach(castItem -> castList.add(castItem.text()));
				}
			}

			String rating = docRankPage.getElementsByClass("ratingColumn").select("strong").get(rankingPosition - 1)
					.text();
			String title = doc.getElementsByClass("sc-b73cd867-0").text();
			String joinedDirectors = String.join(", ", directorsList);
			String joinedCast = String.join(", ", castList);

			doc = connectionWithReviews.get();
			elements = doc.getElementsByClass("lister-item-content");

			int reviewsCounter = 0;

			for (Element element : elements) {

				if (reviewsCounter == 3) {
					break;
				}

				String movieRatingByReviewer = element.getElementsByClass("rating-other-user-rating").select("span")
						.first().text();
				String reviewerName = element.getElementsByClass("display-name-link").text();
				String reviewDate = element.getElementsByClass("review-date").text();
				String reviewContent = element.getElementsByClass("text").text();
				reviews.add("On %s, %s rated the movie as %s and commented: %s".formatted(reviewDate, reviewerName,
						movieRatingByReviewer, reviewContent));
				reviewsCounter += 1;
			}

			movieData.put("Title", title);
			movieData.put("Rating", rating);
			movieData.put("Directors", joinedDirectors);
			movieData.put("Cast", joinedCast);
			movieData.put("Review 1", reviews.get(0));
			movieData.put("Review 2", reviews.get(1));
			movieData.put("Review 3", reviews.get(2));

			return movieData;

		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

}
