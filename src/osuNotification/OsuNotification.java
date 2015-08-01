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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

public class OsuNotification {
	
	private static Connection conn;
	private static Statement stmt;

	public static void main(String[] args) throws SQLException, IOException, TwitterException {
		InputStream is = OsuNotification.class.getResourceAsStream("properties");
        Properties prop = new Properties();
        prop.load(is);
        is.close();
        
        String userId, CK, CS, AT, ATS;
        
        CK = prop.getProperty("CK");
        CS = prop.getProperty("CS");
        AT = prop.getProperty("AT");
        ATS = prop.getProperty("ATS");
        
        Configuration conf = new ConfigurationBuilder()
		.setOAuthConsumerKey(CK).setOAuthConsumerSecret(CS).build();
		AccessToken accessToken = new AccessToken(AT, ATS);
		Twitter twitter = new TwitterFactory(conf).getInstance(accessToken);
		
		userId = prop.getProperty("userId");
		String source = readSource("https://osu.ppy.sh/pages/include/profile-general.php?m=3&u=" + userId);
		
		prepareSQLite();
		
		Matcher reg = Pattern.compile("<b><a href='https:\\/\\/osu.ppy.sh\\/news\\/\\d+'>Performance<\\/a>: (\\d+)pp \\(#([0-9,]+)\\)<\\/b>.+"
				+ "<img class='flag' title='' src='\\/\\/s.ppy.sh\\/images\\/flags\\/jp.gif'\\/><\\/a>#([0-9,]+)<\\/span><\\/div><br\\/>.+"
				+ "<b>Hit Accuracy<\\/b>: ([0-9.]+)%<\\/div><br\\/>.+"
				+ "<b>Play Count<\\/b>: ([0-9]+)<\\/div><br\\/>.+"
				+ "<b>Current Level<\\/b>: ([0-9]+)<\\/div><br\\/><center><table width=300px height=20px class='levelMetre' border=0 cellpadding=0 cellspacing=0><tr><td class='levelPercent' width='132px' align=right>([0-9]+)%<\\/td>.+"
				+ "<td width='42'><img height='42' src='\\/\\/s.ppy.sh\\/images\\/X.png'><\\/td><td width='50'>([0-9]+)<\\/td><td width='42'><img height='42' src='\\/\\/s.ppy.sh\\/images\\/S.png'><\\/td><td width='50'>([0-9]+)<\\/td><td width='42'><img height='42' src='\\/\\/s.ppy.sh\\/images\\/A.png'><\\/td><td width='50'>([0-9]+)<\\/td>")
				.matcher(source);
		if(!reg.find()){
			twitter.updateStatus("osuランキング取得失敗");
			return;
		}
		int pp = Integer.parseInt(reg.group(1).replace(",", ""));
		int rank = Integer.parseInt(reg.group(2).replace(",", ""));
		int jprank = Integer.parseInt(reg.group(3).replace(",", ""));
		double acc = Double.parseDouble(reg.group(4));
		int playcount = Integer.parseInt(reg.group(5).replace(",", ""));
		double level = Double.parseDouble(reg.group(6) + "." + reg.group(7));
		int ss = Integer.parseInt(reg.group(8).replace(",", ""));
		int s = Integer.parseInt(reg.group(9).replace(",", ""));
		int a = Integer.parseInt(reg.group(10).replace(",", ""));
		
		String out;
		ResultSet resultSet = stmt.executeQuery("select * from osuMania where ROWID = (select max(ROWID) from osuMania)");
		if(resultSet.next()){
			int b_pp = resultSet.getInt(1);
			int b_rank = resultSet.getInt(2);
			int b_jprank = resultSet.getInt(3);
			double b_acc = resultSet.getDouble(4);
			int b_playcount = resultSet.getInt(5);
			double b_level = resultSet.getDouble(6);
			int b_ss = resultSet.getInt(7);
			int b_s = resultSet.getInt(8);
			int b_a = resultSet.getInt(9);
			
			String diff_pp = addSignum(String.valueOf(pp - b_pp), true);
			String diff_rank = addSignum(String.valueOf(rank - b_rank), true);
			String diff_jprank = addSignum(String.valueOf(jprank - b_jprank), true);
			String diff_acc = addSignum(String.valueOf(acc - b_acc), false);
			String diff_playcount = addSignum(String.valueOf(playcount - b_playcount), true);
			String diff_level = addSignum(String.valueOf(level - b_level), false);
			String diff_ss = addSignum(String.valueOf(ss - b_ss), true);
			String diff_s = addSignum(String.valueOf(s - b_s), true);
			String diff_a = addSignum(String.valueOf(a - b_a), true);
			
			out = "osu!mania\nPP:" + numberFormat(pp) + "(" + diff_pp + ")\nRank:" + numberFormat(rank) + "(" + diff_rank + ")\nJP_Rank:" +
					numberFormat(jprank) + "(" + diff_jprank + ")\nLv:" + level + "(" + diff_level + ")\nAccuracy:" + acc + "%(" +
					diff_acc + "%)\nPlaycount:" + numberFormat(playcount) + "(" + diff_playcount + ")\nSS:" + numberFormat(ss) + "(" +
					diff_ss + ")\nS:" + numberFormat(s) + "(" + diff_s + ")\nA:" + numberFormat(a) + "(" + diff_a + ")";
		}else{
			out = "osu!mania\nPP:" + numberFormat(pp) + "\nRank:" + numberFormat(rank) + "\nJP_Rank:" + numberFormat(jprank) +
					"\nLv:" + level + "\nAccuracy:" + acc + "%\nPlaycount:" + numberFormat(playcount) + "\nSS:" +
					numberFormat(ss) + "\nS:" + numberFormat(s) + "\nA:" + numberFormat(a);
		}
		stmt.execute("insert into osuMania values(" + pp + "," + rank + "," + jprank + "," + acc + "," +
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
			stmt.execute("create table if not exists osuMania(pp, rank, jprank, acc, playcount, level, ss, s, a)");
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
				d = new BigDecimal(d).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
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
}