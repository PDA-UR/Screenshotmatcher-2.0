function sleep(milliseconds) {
  return new Promise(resolve => setTimeout(resolve, milliseconds));
}

class Timer {
  constructor(name = 'n/a'){
    this.name = name
    this.startTime = 0
    this.time = 0
    this.interval = 0
  }
  start(){
    this.startTime = Date.now()
    this.interval = setInterval(() => {
      this.time = Date.now() - this.startTime
    }, 20);
    return this
  }
  stop(){
    clearInterval(this.interval)
  }

}

class TestSuite {

  constructor(){
      
    this.btnStartTest = $('#btnStartTest')
    this.btnStartVisualTest = $('#btnStartVisualTest')
    this.btnClearLog = $('#btnClearLog')
    this.ulTimes = $('#ulTimes')
    this.logEl = $('#log')

    this.btnStartTest.on('click', () => this.startTest())
    this.btnStartVisualTest.on('click', () => this.startVisualTest())
    this.btnClearLog.on('click', () => this.clearLog())

  }

  clearLog(){
    this.logEl.html("")
  }

  writeLog(...message){
    let newMsg = $('<p/>').html((new Date().toLocaleTimeString()) + ': <b>' + message.join(' ') + '</b>').addClass("uk-margin-remove uk-width-1-1")
    this.logEl.append(newMsg)
  }

  writeImageToLog(name, image, time){

    let imgContainer = $('<div/>').addClass('uk-width-1-3@m').addClass('uk-text-center')

    let newImgDesc = $('<p/>').html(`${name}<br>${time}ms`)
    imgContainer.append(newImgDesc)

    let newImgLink = $('<a/>').attr('href', image).attr('target', '_blank')

    let newImg = $('<img/>').css("width", "180px").attr('src', image)
    newImgLink.append(newImg)
    
    imgContainer.append(newImgLink)
    this.logEl.append(imgContainer)
  }

  async startVisualTest(){

    const timer = new Timer('Main Timer').start()

    this.writeLog('Starting tests')

    // doTest(image, algo, n, name, desc = 'Test')
    // 1: ORB, n = 800
    // 2: SURF, n = 400
    // 3: SIFT, n = 400 (kleiner -> schneller / größer -> präziser)

    // All images
    for(let i = 1; i <= 10; i++){

      await this.doVisualTest(i, 1, 800, "ORB " + i)
      //await sleep(1000)

      await this.doVisualTest(i, 2, 400, "SURF " + i)
      //await sleep(1000)

      await this.doVisualTest(i, 3, 400, "SIFT " + i)
      //await sleep(1000)

    }

    timer.stop()

    this.writeLog(timer.name, timer.time, "ms")

  }

  async startTest(){

    const timer = new Timer('Main Timer').start()

    this.writeLog('Starting tests')

    // doTest(image, algo, n, name, desc = 'Test')
    // 1: ORB, n = 800
    // 2: SURF, n = 400
    // 3: SIFT, n = 400 (kleiner -> schneller / größer -> präziser)

    // All images ORB
    for(let i = 1; i <= 10; i++){
      await this.doTest(i, 1, 800, "ORB " + i)
      //await sleep(1000)
    }

    // All images SURF
    for(let i = 1; i <= 10; i++){
      await this.doTest(i, 2, 400, "SURF " + i)
      //await sleep(1000)
    }

    // All images SIFT
    for(let i = 1; i <= 10; i++){
      await this.doTest(i, 3, 400, "SIFT " + i)
      //await sleep(1000)
    }

    

    timer.stop()

    this.writeLog(timer.name, timer.time, "ms")

  }

  async doTest(image, algo, n, name, desc = 'Test'){
    const timer = new Timer(name).start()

    const testResult = await fetch('/test?image=' + image + '&algo=' + algo + '&n=' + n)

    timer.stop()

    if(testResult.ok){
      const body = await testResult.text()
      this.writeLog(name, "Result:", "<a target='_blank' href='" + body + "'>" + body + "</a>", timer.time, "ms")
    }
    else{
      this.writeLog(name, "Error", timer.time, "ms")
    }
  }

  async doVisualTest(image, algo, n, name, desc = 'Test'){
    const timer = new Timer(name).start()

    const testResult = await fetch('/test?image=' + image + '&algo=' + algo + '&n=' + n)

    timer.stop()

    if(testResult.ok){
      const body = await testResult.text()
      this.writeImageToLog(name, body, timer.time)
    }
    else{
      this.writeImageToLog(name, false, timer.time)
    }
  }
}