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

	public static void main(String[] args) {
		List<String> movieUrls = gatherBottomMoviesUrls();
		int currentRankingPosition = 10;

		for (String url : movieUrls) {
			String reviewsUrl = url + "reviews?sort=userRating&dir=desc&ratingFilter=0";
			Map<String, String> results = getMovieData(url, reviewsUrl, currentRankingPosition);
			results.forEach((key, value) -> System.out.println(key + ": " + value));
			System.out.println("-----------------------------------------------------"
					+ "---------------------------------------------------------"
					+ "-----------------------------------------------");
			currentRankingPosition -= 1;
		}
	}

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
