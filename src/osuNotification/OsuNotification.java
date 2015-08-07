package osuNotification;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.NumberFormat;
import java.util.Properties;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

public class OsuNotification {
	
	private static Connection conn;
	private static Statement stmt;

	public static void main(String[] args) throws SQLException, IOException, TwitterException, JSONException {
		InputStream is = OsuNotification.class.getResourceAsStream("properties");
        Properties prop = new Properties();
        prop.load(is);
        is.close();
        
        String apiKey, user, ck, cs, at, ats;
        
        apiKey = prop.getProperty("apiKey");
        ck = prop.getProperty("ck");
        cs = prop.getProperty("cs");
        at = prop.getProperty("at");
        ats = prop.getProperty("ats");
        
        Configuration conf = new ConfigurationBuilder()
		.setOAuthConsumerKey(ck).setOAuthConsumerSecret(cs).build();
		AccessToken accessToken = new AccessToken(at, ats);
		Twitter twitter = new TwitterFactory(conf).getInstance(accessToken);
		
		user = prop.getProperty("user");
		String source = readSource("https://osu.ppy.sh/api/get_user?m=3&k=" + apiKey + "&u=" + user);
		JSONObject json = new JSONArray(source).getJSONObject(0);
		
		prepareSQLite();
		
		double pp = round_half_up(json.getDouble("pp_raw"));
		int rank = json.getInt("pp_rank");
		String country = json.getString("country");
		int country_rank = json.getInt("pp_country_rank");
		double acc = round_half_up(json.getDouble("accuracy"));
		int playcount = json.getInt("playcount");
		double level = round_half_up(json.getDouble("level"));
		int ss = json.getInt("count_rank_ss");
		int s = json.getInt("count_rank_s");
		int a = json.getInt("count_rank_a");
		
		String out;
		ResultSet resultSet = stmt.executeQuery("select * from osuMania where ROWID = (select max(ROWID) from osuMania)");
		if(resultSet.next()){
			double b_pp = resultSet.getDouble(1);
			int b_rank = resultSet.getInt(2);
			int b_country_rank = resultSet.getInt(3);
			double b_acc = resultSet.getDouble(4);
			int b_playcount = resultSet.getInt(5);
			double b_level = resultSet.getDouble(6);
			int b_ss = resultSet.getInt(7);
			int b_s = resultSet.getInt(8);
			int b_a = resultSet.getInt(9);
			
			String diff_pp = addSignum(String.valueOf(pp - b_pp), false);
			String diff_rank = addSignum(String.valueOf(rank - b_rank), true);
			String diff_country_rank = addSignum(String.valueOf(country_rank - b_country_rank), true);
			String diff_acc = addSignum(String.valueOf(acc - b_acc), false);
			String diff_playcount = addSignum(String.valueOf(playcount - b_playcount), true);
			String diff_level = addSignum(String.valueOf(level - b_level), false);
			String diff_ss = addSignum(String.valueOf(ss - b_ss), true);
			String diff_s = addSignum(String.valueOf(s - b_s), true);
			String diff_a = addSignum(String.valueOf(a - b_a), true);
			
			out = "osu!mania\nPP:" + numberFormatDouble(pp) + "(" + diff_pp + ")\nRank:" + numberFormat(rank) + "(" + diff_rank + ")\n" + country + ":" +
					numberFormat(country_rank) + "(" + diff_country_rank + ")\nLv:" + level + "(" + diff_level + ")\nAcc:" + acc + "%(" +
					diff_acc + "%)\nPlay:" + numberFormat(playcount) + "(" + diff_playcount + ")\nSS:" + numberFormat(ss) + "(" +
					diff_ss + ")\nS:" + numberFormat(s) + "(" + diff_s + ")\nA:" + numberFormat(a) + "(" + diff_a + ")";
		}else{
			out = "osu!mania\nPP:" + numberFormatDouble(pp) + "\nRank:" + numberFormat(rank) + "\n" + country + ":" + numberFormat(country_rank) +
					"\nLv:" + level + "\nAcc:" + acc + "%\nPlay:" + numberFormat(playcount) + "\nSS:" +
					numberFormat(ss) + "\nS:" + numberFormat(s) + "\nA:" + numberFormat(a);
		}
		stmt.execute("insert into osuMania values(" + pp + "," + rank + "," + country_rank + "," + acc + "," +
						playcount + "," + level + "," + ss + "," + s + "," + a + ")");
		
		twitter.updateStatus(out);
		
		stmt.close();
		conn.close();
	}
	
	public static void prepareSQLite(){
		try{
			Class.forName("org.sqlite.JDBC");
			String location = "/home/tao/data/osuDB.db";
			File DB = new File(location);
			if(!DB.exists())
				DB.createNewFile();
			conn = DriverManager.getConnection("jdbc:sqlite:" + location);
			stmt = conn.createStatement();
			stmt.execute("create table if not exists osuMania(pp, rank, country_rank, acc, playcount, level, ss, s, a)");
		}catch(Exception e){
		}
	}
	
	public static String readSource(String url){
		try{
			InputStream in = new URL(url).openStream();
			StringBuilder sb = new StringBuilder();
			try {
				BufferedReader bf = new BufferedReader(new InputStreamReader(in));
				String s;
				while((s=bf.readLine())!=null)
					sb.append(s);
			}finally{
				in.close();
			}
			return sb.toString();
		}catch(Exception e){
			return null;
		}
	}
	
	public static String addSignum(String num, boolean isInt){
		if(num != null){
			if(isInt){
				int i = Integer.parseInt(num);
				if(i > 0)
					return "+" + i;
				else if(i == 0)
					return "0";
				else
					return String.valueOf(i);
			}else{
				double d = Double.parseDouble(num);
				d = round_half_up(d);
				if(d > 0)
					return "+" + d;
				else if(d == 0)
					return "0";
				else
					return String.valueOf(d);
			}
		}else{
			return null;
		}
	}
	
	public static String numberFormat(int i){
		return NumberFormat.getNumberInstance().format(i);
	}
	public static String numberFormatDouble(double d){
		return NumberFormat.getNumberInstance().format(d);
	}
	public static double round_half_up(double d){
		return new BigDecimal(d).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
	}
}