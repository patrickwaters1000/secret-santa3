import React, { Component } from "react";
import ReactDOM from "react-dom";

var handle = null;

var state = {
    token: null,
    everyone: [],
    connected: [],
    giftAssignments: null,
};

const getToken = () => {
    fetch("token", {
        method: 'POST',
        headers: { 'Content-Type': 'application/json;charset=utf-8' }
    }).then(resp => {
        return resp.json();
    }).then(data => {
        state.token = data.token;
        handle.setState(state);
        console.log(`Got token ${data.token}. New state = ${JSON.stringify(state)}`);
    });

};

const identify = (username) => {
    fetch("identify", {
        method: 'POST',
        headers: { 'Content-Type': 'application/json;charset=utf-8' },
        body: JSON.stringify({
            token: state.token,
            username: username
        })
    }).then(resp => {
        return resp.json();
    }).then(data => {
        state.everyone = data.everyone;
        state.connected = data.connected;
        state.giftAssignments = data.giftAssignments;
        handle.setState(state);
    });
};

const poll = () => {
    console.log(`Polling. State = ${JSON.stringify(state)}`);
    fetch("poll", {
        method: 'POST',
        headers: { 'Content-Type': 'application/json;charset=utf-8' },
        body: JSON.stringify({
            token: state.token
        })
    }).then(resp => {
        return resp.json();
    }).then(data => {
        state.everyone = data.everyone;
        state.connected = data.connected;
        state.giftAssignments = data.giftAssignments;
        handle.setState(state);
    });
};

const NameButton = (name, clickable) => {
    let onClick;
    if (clickable) {
        onClick = () => { identify(name); };
    } else {
        onClick = () => {};
    }
    return React.createElement(
        "button",
        {
            onClick: onClick,
            className: (clickable ? "clickableNameButton" : "unclickableNameButton")
        },
        name
    );
};

const NameButtons = (state) => {
    let { everyone, connected } = state;
    /*if (name) {
        return [];
    }*/
    let buttons = everyone.map( n => NameButton(n, !connected.includes(n)));
    return [React.createElement(
        "div",
        {},
        React.createElement(
            "h1",
            {
                font: "arial",
                className: "titleHeader",
            },
            "Who are you?"),
        ...buttons
    )];
};

const ReadyUsers = (state) => {
    let { connected, giftAssignments } = state;
    if (giftAssignments) {
        return [];
    }
    let lines = connected.map(name => React.createElement(
        "h2",
        {
            className: "personReady",
        },
        `${name} is ready!`
    ));
    return [React.createElement(
        "div",
        {},
        ...lines
    )];
};

const GiftAssignments = (state) => {
    let { giftAssignments } = state;
    if (!giftAssignments) {
        return [];
    }
    let lines = giftAssignments.map(name => React.createElement(
        "h2",
        {
            className: "giftAssignment",
        },
        React.createElement(
            "img",
            {
                src: "./gift.svg",
            },
        ),
        name,
    ));
    return [React.createElement(
        "div",
        {},
        React.createElement(
            "h1",
            {
                className: "titleHeader",
            },
            "Your Secret Santa assignments are:"
        ),
        ...lines
    )];
};

class Page extends React.Component {
    constructor(props) {
        super(props);
        handle = this;
    };

    render() {
        let s = this.state;
        if (!s) { return React.createElement("div"); }
        return React.createElement(
            "div",
            {},
            ...NameButtons(s),
            ...ReadyUsers(s),
            ...GiftAssignments(s)
        );
    }
}

window.addEventListener("DOMContentLoaded", () => {
    const div = document.getElementById("main");
    const page = React.createElement(Page, {
        name: null,
        names: [],
        ready: [],
        giftAssignments: null,
    });
    ReactDOM.render(page, div);
    getToken();
    setInterval(poll, 1000);
});
