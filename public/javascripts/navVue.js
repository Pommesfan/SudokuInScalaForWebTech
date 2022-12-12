$("#navAbout").attr("href", "/about");
$("#navGame").attr("href", "/");
$("#navHelp").attr("href", "/help");

const appNav = Vue.createApp({})
appNav.component('phase10nav', {
    props: ['selected'],
    template: `
        <nav class="navbar navbar-expand-lg navbar-light bg-light">
            <a class="navbar-brand " href="#"></a>
            <picture>
               <source media="(min-width:400px)" srcset="@routes.Assets.versioned("images/Phase10-Logo.png")">
               <img class="picture" srcset="@routes.Assets.versioned("images/Phase10-Logo.png")" width="30" height="30" class="d-inline-block align-top" alt="">
            </picture>
            Phase10
            <button class="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbarSupportedContent" aria-controls="navbarSupportedContent" aria-expanded="false" aria-label="Toggle navigation">
                <span class="navbar-toggler-icon"></span>
            </button>

            <div class="collapse navbar-collapse justify-content-center" id="navbarNavAltMarkup">
                <div class="navbar-nav">
                    <a v-if="selected === 'home'" class="nav-link active hoverable text-center" aria-current="page" id="navGame">Game</a>
                    <a v-else class="nav-link hoverable text-center" id="navGame">Game</a>
                    <a v-if="selected === 'about'" class="nav-link active hoverable text-center" aria-current="page" id="navAbout">About</a>
                    <a v-else class="nav-link hoverable text-center" id="navAbout">About</a>
                    <a v-if="selected === 'about'" class="nav-link active hoverable text-center" aria-current="page" id="navHelp">Help</a>
                    <a v-else class="nav-link hoverable text-center" id="navHelp">Help</a>
                </div>
            </div>             
        </nav>
        

            `
})
appNav.mount('#phase10navbar')