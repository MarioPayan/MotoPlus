// Best-effort bike type from the model name; the rider can change it when adding the bike.
export function category(model) {
  const m = model;
  if (/nmax|xmax|tmax|pcx|agility|zuma|vespa|\bdio\b|navi|ntorq|burgman|\bsh ?\d|forza|jupiter|wego|scooter|\blead\b|activa|liberty|beverly|mp3|medley|people|downtown|\blike\b|fiddle|symphony|address|aerox|x-?max|ciao|gts|primavera|sprint|ruckus|majesty|morphous|silver ?wing|reflex|helix|elite|metropolic|adv ?\d{3}|x-?adv/i.test(m)) return 'scooter';
  if (/^(yz(?!f)|wr(?!\d{3}r)|crf(?!\d{3,4}l)|kx|rm-?z|tt-?r|exc|sx-?f?\b|mc\b|ec\b|pw\d|klx|tc\b|te\b|fe\b|fc\b|rr \d)/i.test(m)) return 'offroad';
  if (/t[ée]n[ée]r[ée]|crf\d{3,4}l|\dgs\b|xtz|\bxr ?\d{3}l|v-?strom|africa|\bgs\b|adventure|himalayan|xpulse|tiger|multistrada|desertx|transalp|klr|versys|tracer|norden|scrambler|\badv\b/i.test(m)) return 'trail';
  if (/gold ?wing|fjr|\bgtr\b|\brt\b|k ?1600|electra|ultra|road glide|street glide|tour|roadmaster|chieftain|pursuit/i.test(m)) return 'touring';
  if (/shadow|rebel|vulcan|bolt|v-?star|intruder|boulevard|avenger|meteor|softail|sportster|fat ?bo[by]|breakout|scout|chief|eliminator|diavel|bobber|classic|low ?rider|heritage|super 73|cruiser|nightster|super meteor|springfield/i.test(m)) return 'custom';
  if (/\br ?[13679]\b|\br1[5-9]|cbr|ninja|gsx-?r|\bzx-?\d|\brs ?\d|panigale|\brc ?\d|yzf|rsv|daytona|hayabusa|supersport|\brr\b|tuono v4 rr|s ?1000 ?rr|f4|ns ?200 ?rs|rs ?200/i.test(m)) return 'sport';
  return 'naked';
}

