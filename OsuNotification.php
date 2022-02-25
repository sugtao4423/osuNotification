<?php

declare(strict_types=1);

require_once __DIR__ . '/Config.php';

$osuData = getOsuData();
if ($osuData === null) {
    echo 'No osu! data found.' . PHP_EOL;
    exit(1);
}

$pdo = new PDO('sqlite:' . Config::$sqliteDBLocation);
$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
$pdo->exec('CREATE TABLE IF NOT EXISTS osuMania(pp, rank, country_rank, acc, playcount, level, ss, s, a)');

$stmt = $pdo->query('SELECT * FROM osuMania WHERE ROWID = (SELECT MAX(ROWID) FROM osuMania)');
$lastData = $stmt->fetch();
$lastData = $lastData === false ? null : $lastData;

$insertSql = 'INSERT INTO osuMania VALUES(:pp, :rank, :country_rank, :acc, :playcount, :level, :ss, :s, :a)';
$stmt = $pdo->prepare($insertSql);
$stmt->bindValue(':pp', $osuData['pp_raw'], PDO::PARAM_STR);
$stmt->bindValue(':rank', $osuData['pp_rank'], PDO::PARAM_INT);
$stmt->bindValue(':country_rank', $osuData['pp_country_rank'], PDO::PARAM_INT);
$stmt->bindValue(':acc', $osuData['accuracy'], PDO::PARAM_STR);
$stmt->bindValue(':playcount', $osuData['playcount'], PDO::PARAM_INT);
$stmt->bindValue(':level', $osuData['level'], PDO::PARAM_STR);
$stmt->bindValue(':ss', $osuData['count_rank_ss'], PDO::PARAM_INT);
$stmt->bindValue(':s', $osuData['count_rank_s'], PDO::PARAM_INT);
$stmt->bindValue(':a', $osuData['count_rank_a'], PDO::PARAM_INT);
$stmt->execute();

$tweetText = getTweetText($osuData, $lastData);
tweet($tweetText);


function getOsuData(): ?array
{
    $apiUrl = 'https://osu.ppy.sh/api/get_user?';
    $apiUrl .= http_build_query([
        'm' => 3,
        'k' => Config::$osuApiKey,
        'u' => Config::$osuUserId,
    ]);

    $ch = curl_init();
    curl_setopt_array($ch, [
        CURLOPT_URL            => $apiUrl,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER     => ['Accept: application/json'],
        CURLOPT_ENCODING       => 'gzip, deflate',
    ]);
    $output = curl_exec($ch);
    curl_close($ch);
    if ($output === false) {
        return null;
    }
    $osuData = json_decode($output, true)[0];
    $osuData['pp_raw'] = round((float)$osuData['pp_raw'], 2);
    $osuData['pp_rank'] = (int)$osuData['pp_rank'];
    $osuData['pp_country_rank'] = (int)$osuData['pp_country_rank'];
    $osuData['accuracy'] = round((float)$osuData['accuracy'], 2);
    $osuData['playcount'] = (int)$osuData['playcount'];
    $osuData['level'] = round((float)$osuData['level'], 2);
    $osuData['count_rank_ss'] = (int)$osuData['count_rank_ss'];
    $osuData['count_rank_s'] = (int)$osuData['count_rank_s'];
    $osuData['count_rank_a'] = (int)$osuData['count_rank_a'];

    return $osuData;
}

function getTweetText(array $osuData, ?array $lastData): string
{
    function addSignum($val): string
    {
        return $val > 0 ? ('+' . $val) : (string)$val;
    }

    $nowPP = number_format($osuData['pp_raw']);
    $nowRank = number_format($osuData['pp_rank']);
    $nowCountryRank = number_format($osuData['pp_country_rank']);
    $nowAcc = $osuData['accuracy'];
    $nowPlaycount = number_format($osuData['playcount']);
    $nowLevel = $osuData['level'];
    $nowSS = number_format($osuData['count_rank_ss']);
    $nowS = number_format($osuData['count_rank_s']);
    $nowA = number_format($osuData['count_rank_a']);

    $tweetText = "osu!mania\n";
    if ($lastData === null) {
        $tweetText .= "PP:$nowPP\n";
        $tweetText .= "Rank:$nowRank\n";
        $tweetText .= "$osuData[country]:$nowCountryRank\n";
        $tweetText .= "Lv:$nowLevel\n";
        $tweetText .= "Acc:$nowAcc%\n";
        $tweetText .= "Play:$nowPlaycount\n";
        $tweetText .= "SS:$nowSS\n";
        $tweetText .= "S:$nowS\n";
        $tweetText .= "A:$nowA";
    } else {
        $diffPP = addSignum($osuData['pp_raw'] - $lastData['pp']);
        $diffRank = addSignum($osuData['pp_rank'] - $lastData['rank']);
        $diffCountryRank = addSignum($osuData['pp_country_rank'] - $lastData['country_rank']);
        $diffAcc = addSignum($osuData['accuracy'] - $lastData['acc']);
        $diffPlaycount = addSignum($osuData['playcount'] - $lastData['playcount']);
        $diffLevel = addSignum($osuData['level'] - $lastData['level']);
        $diffSS = addSignum($osuData['count_rank_ss'] - $lastData['ss']);
        $diffS = addSignum($osuData['count_rank_s'] - $lastData['s']);
        $diffA = addSignum($osuData['count_rank_a'] - $lastData['a']);

        $tweetText .= "PP:$nowPP($diffPP)\n";
        $tweetText .= "Rank:$nowRank($diffRank)\n";
        $tweetText .= "$osuData[country]:$nowCountryRank($diffCountryRank)\n";
        $tweetText .= "Lv:$nowLevel($diffLevel)\n";
        $tweetText .= "Acc:$nowAcc%($diffAcc%)\n";
        $tweetText .= "Play:$nowPlaycount($diffPlaycount)\n";
        $tweetText .= "SS:$nowSS($diffSS)\n";
        $tweetText .= "S:$nowS($diffS)\n";
        $tweetText .= "A:$nowA($diffA)";
    }

    return $tweetText;
}

function tweet(string $status = '')
{
    $url = 'https://api.twitter.com/1.1/statuses/update.json';

    $oauthParams = [
        'oauth_consumer_key'     => Config::$twitterConsumerKey,
        'oauth_signature_method' => 'HMAC-SHA1',
        'oauth_timestamp'        => strval(time()),
        'oauth_version'          => '1.0a',
        'oauth_nonce'            => bin2hex(openssl_random_pseudo_bytes(16)),
        'oauth_token'            => Config::$twitterAccessToken,
    ];
    $endpointParams = ['status' => $status];

    $base = $oauthParams + $endpointParams;
    uksort($base, 'strnatcmp');
    $oauthParams['oauth_signature'] = base64_encode(hash_hmac(
        'sha1',
        implode('&', array_map('rawurlencode', [
            'POST',
            $url,
            http_build_query($base, '', '&', PHP_QUERY_RFC3986)
        ])),
        implode('&', array_map('rawurlencode', [Config::$twitterConsumerSecret, Config::$twitterAccessTokenSecret])),
        true
    ));
    $items = [];
    foreach ($oauthParams as $key => $value) {
        $items[] = urlencode($key) . '="' . urlencode($value) . '"';
    }
    $header = 'Authorization: OAuth ' . implode(', ', $items);

    $ch = curl_init();
    curl_setopt_array($ch, [
        CURLOPT_URL            => $url,
        CURLOPT_POST           => true,
        CURLOPT_POSTFIELDS     => http_build_query($endpointParams, '', '&'),
        CURLOPT_HTTPHEADER     => [$header],
        CURLOPT_SSL_VERIFYPEER => false,
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_ENCODING       => 'gzip',
    ]);
    $response = curl_exec($ch);
    curl_close($ch);

    return $response;
}
