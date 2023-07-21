/*ExcludeStart*/
const module = {};
const {checkboxWithName, div, button, table, Signal, text, span, ShowIf, checkbox, label, linkStylesheet} = require('../domHelper');
/*ExcludeEnd*/

module.exports = function (loginState) {
    console.log('Preference adjust Init');
    // static element
    const styles = linkStylesheet('./res/pages/preferenceAdjust.css');

    function onRender() {
        console.log('Course schedule Render');
        styles.mount();
    }

    function onPageOpen() {
        console.log('Course schedule Open');
        // close navLinks when using mobile devices
        window.navMenu.remove('open');
        styles.enable();
        loginState.addListener(onLoginState);
        onLoginState(loginState.state);
    }

    function onPageClose() {
        console.log('Course schedule Close');
        styles.disable();
        loginState.removeListener(onLoginState);
    }

    /**
     * @param {LoginData} state
     */
    function onLoginState(state) {
        if (state && state.login) {

        } else {
            if (state)
                window.askForLoginAlert();
        }
    }

    return div('preferenceAdjust',
        {onRender, onPageOpen, onPageClose},

    );
};