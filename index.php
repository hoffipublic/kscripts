<?php

function timeElapsed(DateTimeImmutable $startDateTime, DateTimeImmutable $endDateTime, bool $onlyCountServiceTimes = false ): DateInterval {

    $openingTime = 8;
    $closingTime = 20;
    $timeZone = new DateTimeZone("Europe/Berlin");
    $startDayOpeningTime = $startDateTime->setTime($openingTime,0);
    $startDayClosingTime = $startDateTime->setTime($closingTime,0);
    $endDayOpeningTime = $endDateTime->setTime($openingTime,0);
    $endDayClosingTime = $endDateTime->setTime($closingTime,0);
    $dailyOpeningDuration = new DateInterval( "PT".($closingTime-$openingTime)."H" );

    // we first eliminate edge cases, for if the issue was solved on the same day or next day until openingTime
    // anything else goes into the "multi-days issue" algorithm below

    if($endDateTime < $startDayOpeningTime){
        // issue was opened and finished before openingTime
        if($onlyCountServiceTimes){
            return new DateInterval("PT0H");
        }
        return $startDateTime->diff($endDateTime);
    }
    if($endDateTime < ($startDayOpeningTime->add(new DateInterval("P1D")))) {
        // solved on the same day or until next day before opening
        if($startDateTime->format("N") >=6 ) {// 1=Monday, 7=Sunday
            return new DateInterval("PT0H");
        }
        if($onlyCountServiceTimes && ($startDateTime > $startDayClosingTime)) {
                return new DateInterval("PT0H");
        }
        $adjustedEndTime = $onlyCountServiceTimes && ($endDateTime > $startDayClosingTime) ? $startDayClosingTime : $endDateTime;
        $adjustedStartTime = $startDateTime < $startDayOpeningTime ? $startDayOpeningTime : $startDateTime;
        return $adjustedStartTime->diff($adjustedEndTime);
    }


    // multi-day issues
    $accumulator = (new DateTime)->setTimestamp(0);

    // handle the first day
    if($startDateTime < $startDayOpeningTime){
        $accumulator->add($dailyOpeningDuration);
    } else if($startDateTime < $startDayClosingTime){
        $accumulator->add($startDateTime->diff($startDayClosingTime));
    }

    // Iterate over days in between
    $iterStep = new DateInterval("P1D");
    $iterDateTime = $startDateTime->add($iterStep);
    $iterEnd = $endDateTime->format("Y-m-d");
    while(strcmp($iterDateTime->format("Y-m-d"), $iterEnd) != 0){
        if(intval($iterDateTime->format("N")) <= 6){
            $accumulator->add($dailyOpeningDuration);
        }
        $iterDateTime = $iterDateTime->add($iterStep);
    }

    // handle last day
    if($onlyCountServiceTimes && (intval($endDateTime->format("N")) == 7)) {
        return (new DateTime)->setTimestamp(0)->diff($accumulator); 
    }

    if(!$onlyCountServiceTimes && ($endDateTime < $endDayOpeningTime)){
        $accumulator->add($endDayClosingTime->sub(new DateInterval("P1D"))->diff($endDateTime));
        if(intval($endDateTime->format("N")) == 1){
            $accumulator->add($dailyOpeningDuration);
        }
    } else if(!$onlyCountServiceTimes) {
        $accumulator->add($endDayOpeningTime->diff($endDateTime));
    } else if($endDateTime > $endDayOpeningTime) {
        $adjustedEndTime = $endDateTime < $endDayClosingTime ? $endDateTime : $endDayClosingTime;
        $accumulator->add($endDayOpeningTime->diff($adjustedEndTime));
    }

    return (new DateTime)->setTimestamp(0)->diff($accumulator); // TODO use secondsToDateIntervall
}

function formatDateInterval(DateInterval $dateInterval): String {
    return $dateInterval->format('P%dDT%hH%iM%sS');
}

function secondsToDateIntervall(int $seconds): DateInterval {
    $d1 = new DateTime();
    $d2 = new DateTime();
    $d2->add(new DateInterval('PT'.$seconds.'S'));
    return $d2->diff($d1);
}

function dateIntervalToSeconds(DateInterval $interval): int {
    $referenceValue = new DateTimeImmutable;
    $newValue = $referenceValue->add($interval);

    return $newValue->getTimestamp() - $referenceValue->getTimestamp();
}

/**
 * Simple helper to debug to the console
 *
 * @param $data object, array, string $data
 * @param $context string  Optional a description.
 *
 * @return string
 */
function debugToConsole($data, $context = 'Debug in Console') {

    // Buffering to solve problems frameworks, like header() in this and not a solid return.
    ob_start();

    $output  = 'console.info(\'' . $context . ':\');';
    $output .= 'console.log(' . json_encode($data) . ');';
    $output  = sprintf('<script>%s</script>', $output);

    echo $output;
}

$testCases = [
    [ "2022-01-03T03:00:00+00:00", "2022-01-03T07:00:00+00:00", "P0DT4H0M0S", "P0DT0H0M0S" ],
    [ "2022-01-03T03:00:00+00:00", "2022-01-03T18:00:00+00:00", "P0DT10H0M0S", "P0DT10H0M0S" ],
    [ "2022-01-03T03:00:00+00:00", "2022-01-03T22:00:00+00:00", "P0DT14H0M0S", "P0DT12H0M0S" ],
    [ "2022-01-03T03:00:00+00:00", "2022-01-04T03:00:00+00:00", "P0DT19H0M0S", "P0DT12H0M0S" ],
    [ "2022-01-03T03:00:00+00:00", "2022-01-04T15:00:00+00:00", "P0DT19H0M0S", "P0DT19H0M0S" ],
    [ "2022-01-03T10:00:00+00:00", "2022-01-09T15:00:00+00:00", "P3DT5H0M0S", "P2DT22H0M0S" ],
    [ "2022-01-03T10:00:00+00:00", "2022-01-10T06:00:00+00:00", "P3DT20H0M0S", "P2DT22H0M0S" ],
    [ "2022-01-03T10:00:00+00:00", "2022-01-10T15:00:00+00:00", "P3DT5H0M0S", "P3DT5H0M0S" ],
    [ "2022-01-03T10:00:00+00:00", "2022-01-10T22:00:00+00:00", "P3DT12H0M0S", "P3DT10H0M0S" ],
    [ "2022-01-03T10:00:00+00:00", "2022-01-11T06:00:00+00:00", "P3DT20H0M0S", "P3DT10H0M0S" ],
    [ "2022-01-03T22:00:00+00:00", "2022-01-11T06:00:00+00:00", "P3DT10H0M0S", "P3DT0H0M0S" ],
    [ "", "", "", "" ],
    // new expected results from hoffi
    // same-day
    //"TuSameDayFullyBeforeMorning"
    [ "2022-01-03T06:50:00+01:00", "2022-01-03T07:01:01+01:00", "P0DT0H11M1S", "P0DT0H0M0S" ],
    //"TuSameDayFullyAfterClosing"
    [ "2022-01-03T22:50:00+01:00", "2022-01-03T23:01:01+01:00", "P0DT0H11M1S", "P0DT0H0M0S" ],
    
    //"SameDayFullyWeekend"
    [ "2022-01-01T09:50:00+01:00", "2022-01-02T04:01:01+01:00", "P0DT0H0M0S", "P0DT0H0M0S" ],

    //"TuSameDayStartBeforeMorning"
    [ "2022-01-03T06:50:00+01:00", "2022-01-03T11:01:01+01:00", "P0DT3H1M1S", "P0DT3H1M1S" ],
    //"TuSameDayStartBeforeMorningEndAfterClosing"
    [ "2022-01-03T06:50:00+01:00", "2022-01-03T22:01:01+01:00", "P0DT14H1M1S", "P0DT12H0M0S" ],

    //"TuSameDayEndAfterClosing"
    [ "2022-01-03T09:50:00+01:00", "2022-01-03T22:01:01+01:00", "P0DT12H11M1S", "P0DT10H10M0S" ],

    //"TuSameDayWithinBusinessTimes"
    [ "2022-01-03T09:50:00+01:00", "2022-01-03T17:01:01+01:00", "P0DT7H11M1S", "P0DT7H11M1S" ],

    // Multi-Days
    //"FrAfterClosing2MondayBusinessTimes"
    [ "2021-12-31T20:50:00+01:00", "2022-01-03T14:01:01+01:00", "P0DT6H1M1S", "P0DT6H1M1S" ],
    //"SuAfterClosing2TuesdayNextWeekBusinessTimes"
    [ "2022-01-02T20:50:00+01:00", "2022-01-11T14:01:01+01:00", "P0DT78H1M1S", "P0DT78H1M1S" ],

    //"TuThWithinBusinessTimes"
    [ "2022-01-04T09:50:00+01:00", "2022-01-06T14:01:01+01:00", "P0DT28H11M1S", "P0DT28H11M1S" ],
    //"TuTh+1WithinBusinessTimes"
    [ "2022-01-04T09:50:00+01:00", "2022-01-13T14:01:01+01:00", "P0DT88H11M1S", "PT88H11M1S" ],

    //"SaSuFullyWeekend"
    [ "2022-01-01T09:50:00+01:00", "2022-01-03T04:01:01+01:00", "P0DT0H0M0S", "P0DT0H0M0S" ],
    //"SaSu+1FullyWeekend"
    [ "2022-01-01T09:50:00+01:00", "2022-01-10T04:01:01+01:00", "P2DT12H0M0S", "P2DT12H0M0S" ],

    //"FrBusinessTimesCompletedWeekend"
    [ "2022-01-07T09:50:00+01:00", "2022-01-10T04:01:01+01:00", "P0DT10H10M0S", "P0DT10H10M0S" ],
];

?>

<html>
<head>
    <style>
      table, th, td {
      padding: 10px;
      border: 1px solid black; 
      border-collapse: collapse;
      }
    </style>
</head>
<body>
    <p>
    <?php $x = new DateInterval("PT0H") ; echo $x->format('P%dDT%hH%iM%sS'); ?></br>
    <?php $x = secondsToDateIntervall(3600) ; echo $x->format('P%dDT%hH%iM%sS'); ?></br>
    </p>
<table>
    <tr>
        <th></th>
        <th>Starttime</th>
        <th>Endtime</th>
        <th>Result</th>
        <th>expected</th>
        <th>Result only service times</th>
        <th>expected</th>
    </tr>
<?php
$i = 0;
foreach($testCases as $testValues){
    $i++;
    echo "<tr>";
    echo "<td>$i</td>";
    if ($testValues[0] == "") { echo "<td>hoffi</td><td></td><td></td><td></td></tr>}" ; continue ; }

    echo "<td>";
    echo $testValues[0];
    echo "</td>";
    echo "<td>";
    echo $testValues[1];
    echo "</td>";

    $result = timeElapsed(new DateTimeImmutable($testValues[0]), new DateTimeImmutable($testValues[1]), false);
    $resultOnlyServiceTimes = timeElapsed(new DateTimeImmutable($testValues[0]), new DateTimeImmutable($testValues[1]), true);

    echo "<td>";
    echo formatDateInterval($result);
    echo "</td>";
    echo "<td>";
    $expected = new DateInterval($testValues[2]);
    if (formatDateInterval($result) == formatDateInterval($expected)) { echo "ok"; } else { echo formatDateInterval($expected) ; }
    echo "</td>";
    echo "<td>";
    echo formatDateInterval($resultOnlyServiceTimes);
    echo "</td>";
    echo "<td>";
    $expected = new DateInterval($testValues[3]);
    if (formatDateInterval($resultOnlyServiceTimes) == formatDateInterval($expected)) { echo "ok"; } else { echo formatDateInterval($expected) ; }
    echo "</td>";
    echo "</tr>";
}
?>
</table>

</body>
</html>