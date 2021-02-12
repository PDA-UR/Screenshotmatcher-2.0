// Control Panel Code

const serverStatusEl = document.querySelector("#server-status")
const refreshStatusEl = document.querySelector("#refresh-status-btn")
const stopServerEl = document.querySelector("#stop-server-btn")
const resultGridEl = document.querySelector("#result-grid")

const stopServer = async () => {
  try {
    let res = await fetch('/stop');
  } catch (error) {
    console.error(error)
  }
}

const heartbeat = async () => {

  let isOnline, hasResults, results, orderedResults = [], serviceURL;

  try {
    let res = await fetch('/heartbeat');
    isOnline = res.ok
  } catch (error) {
    isOnline = false
  }
  
  if(isOnline){

    try {
      let res = await fetch('/get-url');
      if(res.ok){
        serviceURL = await res.text()
        $('#qrcode').html("");
        $('#qrcode').qrcode({ width: 200, height: 200, text: serviceURL });
      }
    } catch (error) {
      serviceURL = ""
    }

    try {
      let res = await fetch('/result-list');
      hasResults = true
      results = await res.text()
    } catch (error) {
      hasResults = false
    }

    if(hasResults){
      const splitted_results = results.split("\n")
      splitted_results.forEach(c => {
        // filter out all non-images
        if(c.indexOf(".jpg") > -1){
          orderedResults.push(c)
        }
      })
      orderedResults = orderedResults.sort().reverse() //sort by newest first

      // fill up grid with images
      resultGridEl.innerHTML = ""
      orderedResults.forEach(c => {
        let imgEl = document.createElement('img')
        imgEl.src = "/results/" + c
        imgEl.width = "200"
        imgEl.height = "200"
        let aEl = document.createElement('a')
        aEl.href = "/results/" + c
        aEl.target = "_blank"
        let liEl = document.createElement('li')
        aEl.appendChild(imgEl)
        liEl.appendChild(aEl)
        resultGridEl.appendChild(liEl)
      })
    }

    serverStatusEl.style.color = 'green'
    serverStatusEl.innerHTML = 'Online'
  }
  else{
    serverStatusEl.style.color = 'red'
    serverStatusEl.innerHTML = 'Offline'
  }

}

heartbeat();
setInterval(heartbeat, 15000);

refreshStatusEl.addEventListener('click', heartbeat)
stopServerEl.addEventListener('click', stopServer)


formElem.onsubmit = async (e) => {
  e.preventDefault();
  const data = new FormData(formElem)
  console.log(data)
  let res = await fetch('/match', {
    method: 'POST',
    body: data
  });
  console.log(await res.text());
};