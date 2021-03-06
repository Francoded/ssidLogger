#!/usr/bin/perl
use strict;

use LWP::UserAgent;
use Storable;
use JSON::Parse;

my $auto_save = 10;
my $bssid_cache = {};
my $cacheFile = '/tmp/bssidCache';
$| = 1;

if (-f $cacheFile) {
  $bssid_cache = retrieve($cacheFile);
}

my $now = time();

print <<PRINTEND
<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="ssidLogger - https://github.com/zordius/ssidLogger" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://www.topografix.com/GPX/1/1" xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd http://www.topografix.com/GPX/gpx_overlay/0/3 http://www.topografix.com/GPX/gpx_overlay/0/3/gpx_overlay.xsd http://www.topografix.com/GPX/gpx_modified/0/1 http://www.topografix.com/GPX/gpx_modified/0/1/gpx_modified.xsd">
 <trk>
  <name>generated by ssidLogger</name>
  <trkseg>
PRINTEND
;

while (<>) {
  chomp;
  my ($time, $log) = split(/\t/, $_);
  my ($ts, $ymd, $his, $cst, $loc) = split(/ /, $time);
  my ($label, $bssid, $signal, $ssid) = split(/ /, $log);

  next if ($label ne "WIFI");

  my $D = bssidLookup($bssid);
  if ($D) {
    my ($sec,$min,$hour,$mday,$mon,$year,$wday,$yday,$isdst) = gmtime($time/1000);
    my $T = sprintf("%04d-%02d-%02dT%02d:%02d:%02dZ", $year+1900, $mon+1, $yday, $hour, $min, $sec);

    print <<PRINTEND
   <trkpt lat="$D->{location}->{lat}" lon="$D->{location}->{lng}">
    <ele>10.000000</ele>
    <time>$T</time>
    <desc>BSSID: $bssid , name: $ssid</desc>
   </trkpt>
PRINTEND
    ;
  }
  if (time() - $now > $auto_save) {
      store $bssid_cache, $cacheFile;
      $now = time();
  }
}

print <<PRINTEND
  </trkseg>
 </trk>
</gpx>
PRINTEND
;

sub bssidLookup {
  my ($bssid) = @_;

  return $bssid_cache->{$bssid} if($bssid_cache->{$bssid});

  my $latlon = getLatLonByBssid($bssid);
  $bssid_cache->{$bssid} = $latlon if ($latlon);

  return $latlon;
}

sub getLatLonByBssid {
  my ($bssid) = @_;
  my $ua = new LWP::UserAgent(
    timeout => 10,
    agent => 'Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1',
    ssl_opts => {
      verify_hostname => 0
    }
  );

  my $res = $ua->get("https://maps.googleapis.com/maps/api/browserlocation/json?browser=firefox&sensor=true&wifi=mac:$bssid");

  return unless (JSON::Parse::valid_json($res->content));
  my $D = JSON::Parse::parse_json($res->content);
  $bssid_cache->{$bssid} = $D;
  return $D;
}
