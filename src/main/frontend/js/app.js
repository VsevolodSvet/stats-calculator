'use strict';

// tag::vars[]
const React = require('react');
const ReactDOM = require('react-dom');
// end::vars[]

// tag::app[]
class App extends React.Component {

	render() {
		return (
			<h1>HELLO</h1>
		)
	}
}
// end::app[]

// tag::user[]
class User extends React.Component {

	render() {
	    let current_user = this.props.user.username + this.props.user.roles.contains("ROLE_ADMIN") ? ' (admin)' : '';
		return (
			<div>{current_user}</div>
		)
	}
}
// end::user[]

// tag::render[]
ReactDOM.render(
	<App />,
	document.getElementById('react')
)
// end::render[]